package com.aashir.ecommerce.service;

import com.aashir.ecommerce.entity.OutboxEvent;
import com.aashir.ecommerce.event.OrderCreatedKafkaEvent;
import com.aashir.ecommerce.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OutboxPublisher {
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Qualifier("dltKafkaTemplate")
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper, KafkaTemplate<String, Object> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedRate = 5000)
    public void publishEvents() {
        var events = outboxEventRepository.findByProcessedAtIsNull();
        List<OutboxEvent> successfullyPublished = new ArrayList<>();
        for(OutboxEvent outboxEvent : events) {
            try {
                if(outboxEvent.getEventType().equals("OrderCreatedKafkaEvent")) {

                    OrderCreatedKafkaEvent event = objectMapper.readValue(
                            outboxEvent.getPayload(),
                            OrderCreatedKafkaEvent.class
                    );

                    kafkaTemplate.send(
                            outboxEvent.getTopic(),
                            event.orderNumber(),
                            event
                    ) .get();

                    outboxEvent.setProcessedAt(java.time.LocalDateTime.now());
                    successfullyPublished.add(outboxEvent);

                }
            }catch (Exception e){
                throw new RuntimeException(
                        "Failed to publish event to outbox events: " + outboxEvent.getId(),
                        e
                );
            }
        }
        if (!successfullyPublished.isEmpty()) {
            outboxEventRepository.saveAll(successfullyPublished);
        }
    }
}
