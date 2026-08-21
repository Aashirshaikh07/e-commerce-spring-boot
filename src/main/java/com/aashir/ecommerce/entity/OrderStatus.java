package com.aashir.ecommerce.entity;

public enum OrderStatus {

    PENDING,
    CONFIRMED,
    CANCELLED,
    SHIPPED,
    DELIVERED;

    public boolean canTransitionTo(OrderStatus newStatus) {
            return switch (this) {
                case PENDING ->
                        newStatus == CONFIRMED ||
                                newStatus == CANCELLED;

                case CONFIRMED ->
                        newStatus == SHIPPED;

                case SHIPPED ->
                        newStatus == DELIVERED;

                case DELIVERED, CANCELLED ->
                        false;
            };
        }
    }
