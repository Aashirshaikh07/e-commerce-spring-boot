package com.aashir.ecommerce.service;

import com.aashir.ecommerce.entity.OutboxEvent;
import com.aashir.ecommerce.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void saveEvent(UUID eventId, String eventType, String topic, Object event){

        try {
            OutboxEvent outboxEvent = new OutboxEvent();

            outboxEvent.setId(eventId);
            outboxEvent.setEventType(eventType);
            outboxEvent.setTopic(topic);
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEvent.setCreatedAt(LocalDateTime.now());
            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException e){
            throw new RuntimeException("Failed to serialize event",e);
        }
    }
}
