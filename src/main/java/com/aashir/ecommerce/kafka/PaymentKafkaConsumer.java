package com.aashir.ecommerce.kafka;

import com.aashir.ecommerce.entity.LatePaymentReview;
import com.aashir.ecommerce.entity.Order;
import com.aashir.ecommerce.entity.OrderStatus;
import com.aashir.ecommerce.entity.ProcessEvent;
import com.aashir.ecommerce.event.PaymentFailedEvent;
import com.aashir.ecommerce.event.PaymentSucceededEvent;
import com.aashir.ecommerce.repository.LatePaymentReviewRepository;
import com.aashir.ecommerce.repository.OrderRepository;
import com.aashir.ecommerce.repository.ProcessedEventRepository;
import com.aashir.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;


@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentKafkaConsumer {
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final LatePaymentReviewRepository latePaymentReviewRepository;

    @KafkaListener(
            topics = "payment-succeeded",
            groupId = "ecommerce-payment-group",
            containerFactory = "paymentSucceededKafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {

        UUID eventId = event.eventId();
        Optional<Order> pendingEventOrder = findOrderForUnprocessedEvent(eventId, event.orderId());
        if (pendingEventOrder.isEmpty()) {
            return;
        }
        Order order = pendingEventOrder.get();

        if (order.getStatus() == OrderStatus.CANCELLED) {
            if (!latePaymentReviewRepository.existsByOrderIdAndPaymentId(order.getId(), event.paymentId())) {
                latePaymentReviewRepository.save(new LatePaymentReview(
                        eventId, order.getId(), event.paymentId(), event.amount()
                ));
            }
            markProcessed(eventId);
            return;
        }

        if (!order.getStatus().canTransitionTo(OrderStatus.CONFIRMED)) {
            log.error(
                    "Payment succeeded for orderId={} but the order is {}. Needs manual review or refund. eventId={}",
                    order.getId(),
                    order.getStatus(),
                    eventId
            );
            markProcessed(eventId);
            return;
        }
        orderService.markPaymentSuccessful(order);

        markProcessed(eventId);
    }

    @KafkaListener(
            topics = "payment-failed",
            groupId = "ecommerce-payment-group",
            containerFactory = "paymentFailedKafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        UUID eventId = event.eventId();

        Optional<Order> pendingEventOrder = findOrderForUnprocessedEvent(eventId, event.orderId());
        if (pendingEventOrder.isEmpty()) {
            return;
        }
        Order order = pendingEventOrder.get();

        log.info("payment-failed received eventId={} orderId={} statusBefore={}", eventId, order.getId(), order.getStatus());
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            log.warn(
                    "Ignoring payment-failed event {} because orderId={} is already {}",
                    eventId,
                    order.getId(),
                    order.getStatus()
            );
            markProcessed(eventId);
            return;
        }
        orderService.cancelOrder(
                order,
                "Payment failed: " + event.reason()
        );

        markProcessed(eventId);
    }

    private Optional<Order> findOrderForUnprocessedEvent(UUID eventId, Long orderId) {
        // Acquire the order lock before the first snapshot read of the event/review tables.
        Optional<Order> order = orderRepository.findByIdForUpdate(orderId);
        if (processedEventRepository.existsById(eventId)) {
            return Optional.empty();
        }
        if (order.isEmpty()) {
            throw new IllegalStateException("Order not found");
        }
        return order;
    }

    private void markProcessed(UUID eventId) {
        ProcessEvent processEvent = new ProcessEvent();
        processEvent.setEventId(eventId);
        processEvent.setProcessedAt(LocalDateTime.now());
        processedEventRepository.save(processEvent);
    }
}
