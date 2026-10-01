package com.aashir.ecommerce.kafka;

import com.aashir.ecommerce.event.PaymentSucceededEvent;
import com.aashir.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class PaymentKafkaConsumer {
    private final OrderService orderService;

    @KafkaListener(
            topics = "payment-events",
            groupId = "order-payment-group"
    )
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        System.out.println("Payment succeeded for order: " + event.orderId());

        orderService.markPaymentSuccessful(event.orderId());
    }
}
