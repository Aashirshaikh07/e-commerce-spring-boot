package com.aashir.ecommerce.service;

import com.aashir.ecommerce.entity.OrderStatus;
import com.aashir.ecommerce.event.OrderCancelledEvent;
import com.aashir.ecommerce.event.OrderCreatedEvent;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @Test
    void sendOrderCreateEmail_shouldSendEmail(){

        OrderCreatedEvent orderCreatedEvent = new OrderCreatedEvent(
                "Ashir",
                "ashir@gmail.com",
                "ORD-123",
                OrderStatus.PENDING,
                List.of(),
                new BigDecimal("89999")
        );

        MimeMessage mimeMessage = Mockito.mock(MimeMessage.class);

        Mockito.when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendOrderCreatedEmail(orderCreatedEvent);

        Mockito.verify(mailSender).send(mimeMessage);
    }
    @Test
    void sendOrderCancelledEmail_shouldSendEmail() {

        OrderCancelledEvent event = new OrderCancelledEvent(
                "Ashir",
                "ashir@gmail.com",
                "ORD-123",
                OrderStatus.CANCELLED,
                List.of(),
                new BigDecimal("89999")
        );

        MimeMessage mimeMessage = Mockito.mock(MimeMessage.class);

        Mockito.when(mailSender.createMimeMessage())
                .thenReturn(mimeMessage);

        emailService.sendOrderCancelledEmail(event);

        Mockito.verify(mailSender)
                .send(mimeMessage);
    }
}
