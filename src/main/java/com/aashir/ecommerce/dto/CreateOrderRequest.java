package com.aashir.ecommerce.dto;

import com.aashir.ecommerce.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class CreateOrderRequest {
    private PaymentMethod paymentMethod;
    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    private PaymentMethod paymentMethod2;

    public CreateOrderRequest() {

    }

    public List<OrderItemRequest> getItems(){
        return items;
    }

    public void setItems(List<OrderItemRequest> items){
        this.items = items;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
