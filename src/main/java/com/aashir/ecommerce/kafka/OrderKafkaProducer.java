package com.aashir.ecommerce.kafka;

import com.aashir.ecommerce.event.OrderCreatedKafkaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderKafkaProducer {
    private static final String TOPIC = "order-events";
    private final KafkaTemplate<String, OrderCreatedKafkaEvent> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedKafkaEvent event) {
        kafkaTemplate.send(TOPIC, event.orderNumber(),event);
    }
}
