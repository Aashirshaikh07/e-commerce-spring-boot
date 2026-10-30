package com.aashir.ecommerce.service;

import com.aashir.ecommerce.entity.Order;
import com.aashir.ecommerce.entity.OrderStatus;
import com.aashir.ecommerce.event.NotificationEvent;
import com.aashir.ecommerce.event.NotificationType;
import com.aashir.ecommerce.event.OrderItemEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class NotificationEventFactory {

    public NotificationEvent orderConfirmed(Order order){
        return orderEvent(order, NotificationType.ORDER_CONFIRMED, "order-confirmed-", OrderStatus.CONFIRMED);
    }

    public NotificationEvent orderCancelled(Order order){
        return orderEvent(order,NotificationType.ORDER_CANCELLED, "order-cancelled", OrderStatus.CANCELLED);
    }

    public NotificationEvent userRegistered(String email, String name) {
        return new NotificationEvent(
                "user-registered-" + email,
                NotificationType.USER_REGISTERED,
                Instant.now(),
                email,
                name,
                Map.of());
    }

    private NotificationEvent orderEvent(Order order, NotificationType type, String idPrefix, OrderStatus status){
        List<OrderItemEvent> items = order.getItems().stream()
                .map(item->new OrderItemEvent(
                        item.getProduct().getProductName(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getSubtotal()))
                .toList();

        Map<String,Object> data = Map.of(
                "orderNumber", order.getOrderNumber(),
                "status", status.name(),
                "items", items,
                "totalAmount", order.getTotalAmount()
        );

        return new NotificationEvent(
                idPrefix + order.getOrderNumber(),
                type,
                Instant.now(),
                order.getUser().getEmail(),
                order.getUser().getName(),
                data);
    }

    public NotificationEvent passwordResetOtp(String email,String name, String otp,int expiresInMinutes,Long otpId){
        return new NotificationEvent(
                "password-reset-" +otpId,
                NotificationType.PASSWORD_RESET_OTP,
                Instant.now(),
                email,
                name,
                Map.of("otp", otp, "expiresInMinutes", expiresInMinutes)
        );
    }
}
