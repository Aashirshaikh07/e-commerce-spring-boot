package com.aashir.ecommerce.controller;

import com.aashir.ecommerce.dto.CreateOrderRequest;
import com.aashir.ecommerce.dto.CreateOrderResponse;
import com.aashir.ecommerce.dto.ResponseUpdatedOrder;
import com.aashir.ecommerce.dto.StatusUpdateRequest;
import com.aashir.ecommerce.entity.OrderStatus;
import com.aashir.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(
        name = "Order Management",
        description = "APIs for managing Order Management"
)
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }


    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request){

        CreateOrderResponse response =  orderService.createOrder(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CreateOrderResponse> getOrderById(@PathVariable Long id){
        CreateOrderResponse response =  orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }



    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseUpdatedOrder> updateOrderStatus(
            @PathVariable Long id, @RequestBody StatusUpdateRequest status
    ){
        OrderStatus orderStatus = status.getStatus();
        ResponseUpdatedOrder response = orderService.updateOrderStatus(id,orderStatus);
        return ResponseEntity.ok(response);
    }
}
