package com.aashir.ecommerce.service;

import com.aashir.ecommerce.event.OrderCancelledEvent;
import com.aashir.ecommerce.event.OrderCreatedEvent;
import com.aashir.ecommerce.event.OrderItemEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOrderCreatedEmail(OrderCreatedEvent event) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(event.customerEmail());
        message.setSubject("Order Confirmed -" + event.customerName());
        message.setText(buildOrderCreatedMessage(event));
        mailSender.send(message);
    }

    public void sendOrderCancelledEmail(OrderCancelledEvent event) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(event.customerEmail());
        message.setSubject("Order Cancelled - " + event.orderNumber());
        message.setText(buildOrderCancelledMessage(event));

        mailSender.send(message);
    }


    private String buildOrderCreatedMessage(OrderCreatedEvent event) {

        StringBuilder message = new StringBuilder();
        message.append("Hi")
                .append(event.customerName())
                .append("\n\n");

        message.append("Your order has been successfully placed!\n\n");

        appendOrderDetails(
                message,
                event.orderNumber(),
                event.status().name(),
                event.items(),
                event.totalAmount()
        );

        message.append("\\nThank you for shopping with us!");

        return message.toString();
    }
    private String buildOrderCancelledMessage(OrderCancelledEvent event) {

        StringBuilder message = new StringBuilder();

        message.append("Hi ")
                .append(event.customerName())
                .append(",\n\n");

        message.append("Your order has been cancelled.\n\n");

        appendOrderDetails(
                message,
                event.orderNumber(),
                event.status().name(),
                event.items(),
                event.totalAmount()
        );

        message.append("\nIf you did not request this cancellation, please contact support.");

        return message.toString();
    }

    private void appendOrderDetails(StringBuilder message, String orderNumber, String status,
            java.util.List<OrderItemEvent> items,
            java.math.BigDecimal totalAmount
    ) {
        message.append("Order Number: ").append(orderNumber).append("\n");
        message.append("Status: ").append(status).append("\n\n");
        message.append("Items:\n");
        message.append("--------------------------------\n");

        for (OrderItemEvent item : items) {

            message.append("Product: ").append(item.productName()).append("\n");
            message.append("Quantity: ").append(item.quantity()).append("\n");
            message.append("Price: ₹").append(item.price()).append("\n");
            message.append("Subtotal: ₹").append(item.subtotal()).append("\n");
            message.append("--------------------------------\n");
        }
        message.append("Total Amount: ₹").append(totalAmount).append("\n");
    }


}