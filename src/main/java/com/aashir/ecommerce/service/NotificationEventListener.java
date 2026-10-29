package com.aashir.ecommerce.service;

import com.aashir.ecommerce.event.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private final NotificationPublisher publisher;

    private static final Logger chain = LoggerFactory.getLogger("NOTIFICATION_CHAIN");
    public NotificationEventListener(NotificationPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotification(NotificationEvent event) {
        chain.info("2 LISTENER after commit, eventId={}", event.eventId());
        publisher.publish(event);
    }
}
