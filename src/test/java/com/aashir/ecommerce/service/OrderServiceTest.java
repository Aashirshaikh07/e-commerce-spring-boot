package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.*;
import com.aashir.ecommerce.entity.*;
import com.aashir.ecommerce.event.NotificationEvent;
import com.aashir.ecommerce.event.NotificationType;
import com.aashir.ecommerce.event.PaymentFailedEvent;
import com.aashir.ecommerce.event.PaymentSucceededEvent;
import com.aashir.ecommerce.exception.*;
import com.aashir.ecommerce.kafka.OrderKafkaProducer;
import com.aashir.ecommerce.kafka.PaymentKafkaConsumer;
import com.aashir.ecommerce.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private OrderKafkaProducer orderKafkaProducer;
    @Mock
    private OutboxService outboxService;
    @Mock
    private OrderIdempotencyRepository orderIdempotencyRepository;
    @Mock
    private OrderRequestFingerprint orderRequestFingerprint;
    @Mock
    private OrderCreationTransactionService orderCreationTransactionService;
    @Mock
    private OrderIdempotencyRecoveryService orderIdempotencyRecoveryService;
    @Mock
    private NotificationEventFactory notificationEventFactory;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private LatePaymentReviewRepository latePaymentReviewRepository;

    private SimpleMeterRegistry meterRegistry;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        orderService = new OrderService(
                orderRepository,
                productRepository,
                orderItemRepository,
                inventoryRepository,
                inventoryService,
                userRepository,
                eventPublisher,
                orderKafkaProducer,
                outboxService,
                orderIdempotencyRepository,
                orderRequestFingerprint,
                orderCreationTransactionService,
                orderIdempotencyRecoveryService,
                notificationEventFactory,
                meterRegistry);
    }

    private Order order(OrderStatus status) {
        User user = new User();
        user.setName("Aashir");
        user.setEmail("a@b.com");

        Order order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORD-1001");
        order.setTotalAmount(new BigDecimal("1598.00"));
        order.setUser(user);
        order.setStatus(status);
        return order;
    }

    private Order orderWithItem(OrderStatus status) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(10L);

        OrderItem item = mock(OrderItem.class);
        when(item.getProduct()).thenReturn(product);
        when(item.getQuantity()).thenReturn(2);

        Order order = order(status);
        order.setItems(List.of(item));
        return order;
    }

    private NotificationEvent notification(NotificationType type) {
        return new NotificationEvent("evt-1", type, Instant.now(), "a@b.com", "Aashir", Map.of());
    }

    @Test
    void markPaymentSuccessfulConfirmsPendingOrderAndPublishesNotification() {
        Order order = order(OrderStatus.PENDING);
        NotificationEvent notification = notification(NotificationType.ORDER_CONFIRMED);
        when(notificationEventFactory.orderConfirmed(order)).thenReturn(notification);

        orderService.markPaymentSuccessful(order);

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verify(orderRepository).save(order);
        verify(eventPublisher).publishEvent(notification);
    }

    @Test
    void lateCaptureAndReplayLeaveAnExpiredOrderCancelled() {
        Order order = orderWithItem(OrderStatus.PENDING);
        PaymentKafkaConsumer consumer = paymentConsumerFor(order);
        NotificationEvent cancellation = notification(NotificationType.ORDER_CANCELLED);
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(order.getItems());
        when(notificationEventFactory.orderCancelled(order)).thenReturn(cancellation);
        var expiry = new PaymentFailedEvent(UUID.randomUUID(), 10L, order.getId(), "Payment expired");
        var capture = new PaymentSucceededEvent(UUID.randomUUID(), 10L, order.getId(), order.getTotalAmount());

        consumer.handlePaymentFailed(expiry);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        consumer.handlePaymentSucceeded(capture);
        consumer.handlePaymentSucceeded(capture);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderRepository).save(order);
        verify(inventoryService).addStock(10L, new UpdateStockRequest(2));
        verify(eventPublisher).publishEvent(cancellation);
        verify(notificationEventFactory, never()).orderConfirmed(order);
        assertEventsProcessedOnce(expiry.eventId(), capture.eventId());
    }

    @Test
    void lateFailureAndSuccessReplayLeaveAConfirmedOrderUntouched() {
        Order order = order(OrderStatus.PENDING);
        PaymentKafkaConsumer consumer = paymentConsumerFor(order);
        NotificationEvent confirmation = notification(NotificationType.ORDER_CONFIRMED);
        when(notificationEventFactory.orderConfirmed(order)).thenReturn(confirmation);
        var capture = new PaymentSucceededEvent(UUID.randomUUID(), 10L, order.getId(), order.getTotalAmount());
        var expiry = new PaymentFailedEvent(UUID.randomUUID(), 10L, order.getId(), "Payment expired");

        consumer.handlePaymentSucceeded(capture);
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        consumer.handlePaymentFailed(expiry);
        consumer.handlePaymentSucceeded(capture);

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verify(orderRepository).save(order);
        verify(eventPublisher).publishEvent(confirmation);
        verify(inventoryService, never()).addStock(any(Long.class), any(UpdateStockRequest.class));
        verify(notificationEventFactory, never()).orderCancelled(order);
        assertEventsProcessedOnce(capture.eventId(), expiry.eventId());
    }

    private PaymentKafkaConsumer paymentConsumerFor(Order order) {
        Set<UUID> processedIds = new HashSet<>();
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(processedEventRepository.existsById(any(UUID.class)))
                .thenAnswer(call -> processedIds.contains(call.getArgument(0)));
        when(processedEventRepository.save(any(ProcessEvent.class))).thenAnswer(call -> {
            ProcessEvent event = call.getArgument(0);
            processedIds.add(event.getEventId());
            return event;
        });
        return new PaymentKafkaConsumer(
                orderService, orderRepository, processedEventRepository, latePaymentReviewRepository
        );
    }

    private void assertEventsProcessedOnce(UUID firstId, UUID secondId) {
        verify(processedEventRepository).save(argThat(event -> firstId.equals(event.getEventId())));
        verify(processedEventRepository).save(argThat(event -> secondId.equals(event.getEventId())));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDING")
    void markPaymentSuccessfulRejectsEveryStatusExceptPending(OrderStatus status) {
        Order order = order(status);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> orderService.markPaymentSuccessful(order));

        assertEquals("Order cannot be confirmed from status " + status, exception.getMessage());
        assertEquals(status, order.getStatus());
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void cancelOrderCancelsRestoresStockAndPublishesNotification() {
        Order order = orderWithItem(OrderStatus.PENDING);
        NotificationEvent notification = notification(NotificationType.ORDER_CANCELLED);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(order.getItems());
        when(notificationEventFactory.orderCancelled(order)).thenReturn(notification);

        orderService.cancelOrder(order, "Payment failed");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(inventoryService).addStock(eq(10L), any(UpdateStockRequest.class));
        verify(orderRepository).save(order);

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(published.capture());
        assertTrue(published.getAllValues().contains(notification));

        assertEquals(1.0, meterRegistry.counter("orders.cancelled").count());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PENDING")
    void cancelOrderRejectsEveryStatusExceptPending(OrderStatus status) {
        Order order = order(status);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStatusTransitionException.class,
                () -> orderService.cancelOrder(order, "Payment failed"));

        assertEquals(status, order.getStatus());
        verify(orderRepository, never()).save(any());
        verify(inventoryService, never()).addStock(any(Long.class), any(UpdateStockRequest.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void updateOrderStatusFailsForUnknownOrder() {
        when(orderRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFountException.class,
                () -> orderService.updateOrderStatus(99L, OrderStatus.CANCELLED));
    }

    @Test
    void updateOrderStatusMovesConfirmedOrderToShippedWithoutSideEffects() {
        Order order = order(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        orderService.updateOrderStatus(1L, OrderStatus.SHIPPED);

        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        verify(orderRepository).save(order);
        verify(inventoryService, never()).addStock(any(Long.class), any(UpdateStockRequest.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void updateOrderStatusRejectsAnInvalidJump() {
        Order order = order(OrderStatus.PENDING);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStatusTransitionException.class,
                () -> orderService.updateOrderStatus(1L, OrderStatus.DELIVERED));

        assertEquals(OrderStatus.PENDING, order.getStatus());
        verify(orderRepository, never()).save(any());
    }
}
