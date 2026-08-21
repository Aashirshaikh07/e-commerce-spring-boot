package com.aashir.ecommerce.dto;

import com.aashir.ecommerce.entity.OrderStatus;

public class StatusUpdateRequest {

    private OrderStatus status;

    public OrderStatus getStatus() {
        return status;
    }
    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
