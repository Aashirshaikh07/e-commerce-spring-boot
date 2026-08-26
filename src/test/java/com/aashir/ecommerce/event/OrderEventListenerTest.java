package com.aashir.ecommerce.event;

import com.aashir.ecommerce.entity.OrderStatus;
import com.aashir.ecommerce.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class OrderEventListenerTest {
    @Mock
    private EmailService emailService;

    @InjectMocks
    private OrderEventListener orderEventListener;

    @Test
    void shouldSendOrderCreatedEmail() {

        OrderCreatedEvent event = new OrderCreatedEvent(
                "Ashir",
                "ashir@gmail.com",
                "ORD-123",
                OrderStatus.PENDING,
                java.util.List.of(),
                java.math.BigDecimal.valueOf(89999)
        );

        orderEventListener.handleOrderCreated(event);
        verify(emailService).sendOrderCreatedEmail(event);
    }

    @Test
    void shouldSendOrderCancelledEmail() {

        OrderCancelledEvent event = new OrderCancelledEvent(
                "Ashir",
                "ashir@gmail.com",
                "ORD-123",
                OrderStatus.CANCELLED,
                java.util.List.of(),
                java.math.BigDecimal.valueOf(89999)
        );

        orderEventListener.handleOrderCancelled(event);

        verify(emailService).sendOrderCancelledEmail(event);
    }


}
