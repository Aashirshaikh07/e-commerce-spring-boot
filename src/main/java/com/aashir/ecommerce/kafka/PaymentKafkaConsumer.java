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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;


@Component
@RequiredArgsConstructor
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
    public void handlePaymentFailed(PaymentFailedEvent event) {
        UUID eventId = event.eventId();

        if (processedEventRepository.existsById(eventId)) {
            return;
        }
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(()-> new RuntimeException("Order not found"));

        orderService.cancelOrder(
                order,
                "Payment failed: " + event.reason()
        );

        ProcessEvent processedEvent = new ProcessEvent();
        processedEvent.setEventId(eventId);
        processedEvent.setProcessedAt(LocalDateTime.now());

        processedEventRepository.save(processedEvent);
    }
}
