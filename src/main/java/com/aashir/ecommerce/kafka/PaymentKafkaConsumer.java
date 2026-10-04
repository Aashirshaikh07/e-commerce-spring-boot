package com.aashir.ecommerce.kafka;

import com.aashir.ecommerce.event.PaymentFailedEvent;
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
            topics = "payment-succeeded",
            groupId = "ecommerce-payment-group",
            containerFactory = "paymentSucceededKafkaListenerContainerFactory"
    )
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        System.out.println("Payment succeeded for order: " + event.orderId());

        orderService.markPaymentSuccessful(event.orderId());
    }

    @KafkaListener(
            topics = "payment-failed",
            groupId = "ecommerce-payment-group",
            containerFactory = "paymentFailedKafkaListenerContainerFactory"
    )
    public void handlePaymentFailed(PaymentFailedEvent event) {
        System.out.println(
                "Payment failed for order: " + event.orderId()
        );

        orderService.cancelOrder(
                event.orderId(),
                "Payment failed: " + event.reason()
        );
    }
}
