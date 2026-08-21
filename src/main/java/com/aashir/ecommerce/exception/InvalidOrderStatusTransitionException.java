package com.aashir.ecommerce.exception;

import com.aashir.ecommerce.entity.OrderStatus;

public class InvalidOrderStatusTransitionException extends  RuntimeException{
    public InvalidOrderStatusTransitionException(
            OrderStatus currentStatus,
            OrderStatus requestedStatus) {

        super("Cannot change order status from "
                + currentStatus
                + " to "
                + requestedStatus);
    }
}

