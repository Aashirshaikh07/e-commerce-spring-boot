package com.aashir.ecommerce.kafka;

import com.aashir.ecommerce.entity.Order;
import com.aashir.ecommerce.entity.OrderStatus;
import com.aashir.ecommerce.entity.ProcessEvent;
import com.aashir.ecommerce.event.PaymentFailedEvent;
import com.aashir.ecommerce.event.PaymentSucceededEvent;
import com.aashir.ecommerce.repository.OrderRepository;
import com.aashir.ecommerce.repository.ProcessedEventRepository;
import com.aashir.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;


@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentKafkaConsumer {
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(
            topics = "payment-succeeded",
            groupId = "ecommerce-payment-group",
            containerFactory = "paymentSucceededKafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {

        UUID eventId =event.eventId();
        if (processedEventRepository.existsById(eventId)) {
            return;
        }
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(()-> new RuntimeException("Order not found"));

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

        ProcessEvent  processEvent = new ProcessEvent();
        processEvent.setEventId(eventId);
        processEvent.setProcessedAt(LocalDateTime.now());

        processedEventRepository.save(processEvent);
    }

    @KafkaListener(
            topics = "payment-failed",
            groupId = "ecommerce-payment-group",
            containerFactory = "paymentFailedKafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        UUID eventId = event.eventId();

        if (processedEventRepository.existsById(eventId)) {
            return;
        }
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(()-> new RuntimeException("Order not found"));

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

        ProcessEvent processedEvent = new ProcessEvent();
        processedEvent.setEventId(eventId);
        processedEvent.setProcessedAt(LocalDateTime.now());

        processedEventRepository.save(processedEvent);
    }
    private void markProcessed(UUID eventId) {
        ProcessEvent processEvent = new ProcessEvent();
        processEvent.setEventId(eventId);
        processEvent.setProcessedAt(LocalDateTime.now());
        processedEventRepository.save(processEvent);
    }
}
