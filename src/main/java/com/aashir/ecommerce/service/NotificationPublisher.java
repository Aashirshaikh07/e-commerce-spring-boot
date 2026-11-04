package com.aashir.ecommerce.service;

import com.aashir.ecommerce.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;


@Component
public class NotificationPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);
    private final String topic;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final Logger chain = LoggerFactory.getLogger("NOTIFICATION_CHAIN");

    public NotificationPublisher(@Value("${app.kafka.notification-topic}") String topic, KafkaTemplate<String, Object> kafkaTemplate) {
        this.topic = topic;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Async("notificationExecutor")
    public void publish(NotificationEvent event){
        chain.info("3 PUBLISHER sending to kafka, eventId={}", event.eventId());

        try {
            kafkaTemplate.send(topic,event.recipientEmail(),event).whenComplete((result, ex)->{
                if(ex!=null){
                    log.error("Failed to deliver notification event id={}", event.eventId(), ex);
                } else {
                    chain.info("4 KAFKA acknowledged, eventId={}", event.eventId());
                }
            });
        }catch (RuntimeException e){
            chain.error("3 PUBLISHER exception, eventId={}, error={}", event.eventId(), e.toString());
        }

    }
}
