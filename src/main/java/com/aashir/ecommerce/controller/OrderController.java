package com.aashir.ecommerce.controller;

import com.aashir.ecommerce.dto.CreateOrderRequest;
import com.aashir.ecommerce.dto.CreateOrderResponse;
import com.aashir.ecommerce.dto.ResponseUpdatedOrder;
import com.aashir.ecommerce.dto.StatusUpdateRequest;
import com.aashir.ecommerce.entity.OrderStatus;
import com.aashir.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request){

        CreateOrderResponse response =  orderService.createOrder(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreateOrderResponse> getOrderById(@PathVariable Long id){
        CreateOrderResponse response =  orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }


    @PatchMapping("/{id}/status")
    public ResponseEntity<ResponseUpdatedOrder> updateOrderStatus(
            @PathVariable Long id, @RequestBody StatusUpdateRequest status
    ){
        OrderStatus orderStatus = status.getStatus();
        ResponseUpdatedOrder response = orderService.updateOrderStatus(id,orderStatus);
        return ResponseEntity.ok(response);
    }
}
