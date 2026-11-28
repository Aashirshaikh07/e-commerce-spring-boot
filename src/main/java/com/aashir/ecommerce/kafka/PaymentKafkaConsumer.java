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

import static com.aashir.ecommerce.config.TransactionCallbacks.afterCommit;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentKafkaConsumer {
    private static final String PAYMENT_SUCCEEDED_TOPIC = "payment-succeeded";
    private static final String PAYMENT_FAILED_TOPIC = "payment-failed";

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final LatePaymentReviewRepository latePaymentReviewRepository;

    @KafkaListener(
            topics = PAYMENT_SUCCEEDED_TOPIC,
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
                afterCommit(() -> log.atWarn()
                        .addKeyValue("orderId", event.orderId())
                        .addKeyValue("eventId", eventId)
                        .addKeyValue("paymentId", event.paymentId())
                        .addKeyValue("topic", PAYMENT_SUCCEEDED_TOPIC)
                        .log("Late payment capture recorded for review"));
            }
            markProcessed(eventId);
            return;
        }

        if (!order.getStatus().canTransitionTo(OrderStatus.CONFIRMED)) {
            log.atError()
                    .addKeyValue("orderId", event.orderId())
                    .addKeyValue("eventId", eventId)
                    .addKeyValue("paymentId", event.paymentId())
                    .addKeyValue("topic", PAYMENT_SUCCEEDED_TOPIC)
                    .addKeyValue("orderStatus", order.getStatus())
                    .log("Payment capture requires review because the order cannot be confirmed");
            markProcessed(eventId);
            return;
        }
        orderService.markPaymentSuccessful(order);

        markProcessed(eventId);
        afterCommit(() -> log.atInfo()
                .addKeyValue("orderId", event.orderId())
                .addKeyValue("eventId", eventId)
                .addKeyValue("paymentId", event.paymentId())
                .addKeyValue("topic", PAYMENT_SUCCEEDED_TOPIC)
                .log("Payment success confirmed order"));
    }

    @KafkaListener(
            topics = PAYMENT_FAILED_TOPIC,
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

        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            log.atWarn()
                    .addKeyValue("orderId", event.orderId())
                    .addKeyValue("eventId", eventId)
                    .addKeyValue("paymentId", event.paymentId())
                    .addKeyValue("topic", PAYMENT_FAILED_TOPIC)
                    .addKeyValue("orderStatus", order.getStatus())
                    .log("Skipped payment failure for current order status");
            markProcessed(eventId);
            return;
        }
        orderService.cancelOrder(
                order,
                "Payment failed: " + event.reason()
        );

        markProcessed(eventId);
        afterCommit(() -> log.atInfo()
                .addKeyValue("orderId", event.orderId())
                .addKeyValue("eventId", eventId)
                .addKeyValue("paymentId", event.paymentId())
                .addKeyValue("topic", PAYMENT_FAILED_TOPIC)
                .log("Payment failure cancelled order"));
    }

    private Optional<Order> findOrderForUnprocessedEvent(UUID eventId, Long orderId) {
        // Acquire the order lock before the first snapshot read of the event/review tables.
        Optional<Order> order = orderRepository.findByIdForUpdate(orderId);
        if (processedEventRepository.existsById(eventId)) {
            log.atDebug()
                    .addKeyValue("orderId", orderId)
                    .addKeyValue("eventId", eventId)
                    .log("Payment event already processed");
            return Optional.empty();
        }
        if (order.isEmpty()) {
            throw new IllegalStateException("Order " + orderId + " not found for payment event " + eventId);
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
