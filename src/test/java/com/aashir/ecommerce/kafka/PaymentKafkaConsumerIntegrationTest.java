package com.aashir.ecommerce.kafka;

import com.aashir.ecommerce.entity.Inventory;
import com.aashir.ecommerce.entity.Order;
import com.aashir.ecommerce.entity.OrderItem;
import com.aashir.ecommerce.entity.OrderStatus;
import com.aashir.ecommerce.entity.Product;
import com.aashir.ecommerce.entity.User;
import com.aashir.ecommerce.entity.UserStatus;
import com.aashir.ecommerce.event.PaymentFailedEvent;
import com.aashir.ecommerce.event.PaymentSucceededEvent;
import com.aashir.ecommerce.repository.InventoryRepository;
import com.aashir.ecommerce.repository.LatePaymentReviewRepository;
import com.aashir.ecommerce.repository.OrderRepository;
import com.aashir.ecommerce.repository.ProcessedEventRepository;
import com.aashir.ecommerce.repository.ProductRepository;
import com.aashir.ecommerce.repository.UserRepository;
import com.aashir.ecommerce.service.InventoryService;
import com.aashir.ecommerce.service.NotificationEventFactory;
import com.aashir.ecommerce.service.OrderCreationTransactionService;
import com.aashir.ecommerce.service.OrderIdempotencyRecoveryService;
import com.aashir.ecommerce.service.OrderRequestFingerprint;
import com.aashir.ecommerce.service.OrderService;
import com.aashir.ecommerce.service.OutboxService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=create-only")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentKafkaConsumer.class, OrderService.class, InventoryService.class,
        NotificationEventFactory.class, SimpleMeterRegistry.class})
@MockitoBean(types = {OrderKafkaProducer.class, OutboxService.class, OrderRequestFingerprint.class,
        OrderCreationTransactionService.class, OrderIdempotencyRecoveryService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PaymentKafkaConsumerIntegrationTest {

    private static final BigDecimal CAPTURED_AMOUNT = new BigDecimal("1598.00");

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private PaymentKafkaConsumer consumer;
    @Autowired
    private LatePaymentReviewRepository reviews;
    @Autowired
    private ProcessedEventRepository processedEvents;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ProductRepository products;
    @Autowired
    private InventoryRepository inventories;
    @MockitoSpyBean
    private NotificationEventFactory notifications;
    @MockitoBean
    private CacheManager cacheManager;

    @BeforeEach
    void clearEventTables() {
        reviews.deleteAllInBatch();
        processedEvents.deleteAllInBatch();
    }

    @Test
    void recordsCaptureForCancelledOrderAndMarksEventProcessed() {
        PaymentSucceededEvent event = captureForCancelledOrder();

        consumer.handlePaymentSucceeded(event);
        consumer.handlePaymentSucceeded(event);

        assertReviewRecorded(event);
        assertEquals(1, reviews.count());
    }

    @Test
    void newEventIdForTheSameCaptureKeepsTheOriginalReview() {
        PaymentSucceededEvent original = captureForCancelledOrder();
        var replay = new PaymentSucceededEvent(
                UUID.randomUUID(), original.paymentId(), original.orderId(), original.amount()
        );

        consumer.handlePaymentSucceeded(original);
        consumer.handlePaymentSucceeded(replay);

        assertReviewRecorded(original);
        assertEquals(1, reviews.count());
        assertTrue(processedEvents.existsById(replay.eventId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void overlappingCaptureAndExpiryHaveOnlyOneOrderOutcome(boolean captureFirst) throws Exception {
        Product product = new Product();
        product.setProductName("Test product");
        product.setCategory("Test");
        product.setPrice(CAPTURED_AMOUNT);
        Product savedProduct = products.save(product);
        Inventory inventory = new Inventory();
        inventory.setProduct(savedProduct);
        inventory.setQuantity(4);
        inventories.save(inventory);

        Order order = newOrder(OrderStatus.PENDING);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(savedProduct);
        item.setPrice(CAPTURED_AMOUNT);
        item.setSubtotal(CAPTURED_AMOUNT);
        item.setQuantity(1);
        order.getItems().add(item);
        Long orderId = orders.save(order).getId();
        var capture = new PaymentSucceededEvent(UUID.randomUUID(), 10L, orderId, CAPTURED_AMOUNT);
        var expiry = new PaymentFailedEvent(UUID.randomUUID(), 10L, orderId, "Payment expired");
        Runnable captureOrder = () -> consumer.handlePaymentSucceeded(capture);
        Runnable cancelOrder = () -> consumer.handlePaymentFailed(expiry);
        Runnable first = captureFirst ? captureOrder : cancelOrder;
        Runnable second = captureFirst ? cancelOrder : captureOrder;

        CountDownLatch firstHandlerFinished = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondHandlerStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstResult = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                first.run();
                firstHandlerFinished.countDown();
                await(allowFirstCommit);
            }));
            await(firstHandlerFinished);
            var secondResult = executor.submit(() -> {
                secondHandlerStarted.countDown();
                second.run();
            });
            await(secondHandlerStarted);
            assertThrows(TimeoutException.class, () -> secondResult.get(500, TimeUnit.MILLISECONDS));
            allowFirstCommit.countDown();
            firstResult.get(20, TimeUnit.SECONDS);
            secondResult.get(20, TimeUnit.SECONDS);
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();
        }

        assertTrue(processedEvents.existsById(capture.eventId()));
        assertTrue(processedEvents.existsById(expiry.eventId()));
        int quantity = inventories.findByProductId(savedProduct.getId()).orElseThrow().getQuantity();
        OrderStatus status = orders.findById(orderId).orElseThrow().getStatus();
        if (captureFirst) {
            assertEquals(OrderStatus.CONFIRMED, status);
            assertEquals(4, quantity);
            assertEquals(0, reviews.count());
            verify(notifications).orderConfirmed(any(Order.class));
            verify(notifications, never()).orderCancelled(any(Order.class));
        } else {
            assertEquals(OrderStatus.CANCELLED, status);
            assertEquals(5, quantity);
            assertEquals(1, reviews.count());
            assertTrue(reviews.existsById(capture.eventId()));
            verify(notifications).orderCancelled(any(Order.class));
            verify(notifications, never()).orderConfirmed(any(Order.class));
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(20, TimeUnit.SECONDS), "Timed out waiting for the other transaction");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for the other transaction", exception);
        }
    }

    @Test
    void failedReviewWriteLeavesEventEligibleForRetry() {
        PaymentSucceededEvent event = captureForCancelledOrder();
        jdbc.execute("""
                ALTER TABLE late_payment_reviews ADD CONSTRAINT test_reject_review
                CHECK (captured_amount <> 1598.00)
                """);
        try {
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> consumer.handlePaymentSucceeded(event));
            assertTrue(NestedExceptionUtils.getMostSpecificCause(failure).getMessage()
                    .contains("test_reject_review"), failure::toString);
            assertFalse(reviews.existsById(event.eventId()));
            assertFalse(processedEvents.existsById(event.eventId()));
        } finally {
            jdbc.execute("ALTER TABLE late_payment_reviews DROP CHECK test_reject_review");
        }

        consumer.handlePaymentSucceeded(event);

        assertReviewRecorded(event);
    }

    @Test
    void failedProcessedEventWriteRollsBackReview() {
        PaymentSucceededEvent event = captureForCancelledOrder();
        jdbc.execute("""
                ALTER TABLE processed_events ADD CONSTRAINT test_reject_processed
                CHECK (processed_at < '2000-01-01')
                """);
        try {
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> consumer.handlePaymentSucceeded(event));
            assertTrue(NestedExceptionUtils.getMostSpecificCause(failure).getMessage()
                    .contains("test_reject_processed"), failure::toString);
            assertFalse(reviews.existsById(event.eventId()));
            assertFalse(processedEvents.existsById(event.eventId()));
        } finally {
            jdbc.execute("ALTER TABLE processed_events DROP CHECK test_reject_processed");
        }

        consumer.handlePaymentSucceeded(event);

        assertReviewRecorded(event);
    }

    private PaymentSucceededEvent captureForCancelledOrder() {
        Long orderId = orders.save(newOrder(OrderStatus.CANCELLED)).getId();
        return new PaymentSucceededEvent(UUID.randomUUID(), 10L, orderId, CAPTURED_AMOUNT);
    }

    private Order newOrder(OrderStatus status) {
        String reference = UUID.randomUUID().toString();
        User user = new User();
        user.setName("Test Customer");
        user.setEmail(reference + "@test.local");
        user.setPhone(reference);
        user.setPassword("unused");
        user.setRole("USER");
        user.setStatus(UserStatus.ACTIVE);

        Order order = new Order();
        order.setUser(users.save(user));
        order.setOrderNumber(reference);
        order.setTotalAmount(CAPTURED_AMOUNT);
        order.setStatus(status);
        return order;
    }

    private void assertReviewRecorded(PaymentSucceededEvent event) {
        var review = reviews.findById(event.eventId()).orElseThrow();
        assertEquals(event.orderId(), review.getOrderId());
        assertEquals(event.paymentId(), review.getPaymentId());
        assertEquals(event.amount(), review.getCapturedAmount());
        assertNotNull(review.getCreatedAt());
        assertTrue(processedEvents.existsById(event.eventId()));
        assertEquals(OrderStatus.CANCELLED, orders.findById(event.orderId()).orElseThrow().getStatus());
        verifyNoInteractions(notifications);
    }
}
