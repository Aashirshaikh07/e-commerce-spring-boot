package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.*;
import com.aashir.ecommerce.entity.*;
import com.aashir.ecommerce.exception.*;
import com.aashir.ecommerce.repository.OrderItemRepository;
import com.aashir.ecommerce.repository.OrderRepository;
import com.aashir.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest) {
        Order order = new Order();

        order.setOrderNumber("ORD-"+ UUID.randomUUID());
        order.setStatus(OrderStatus.PENDING);

        BigDecimal totalAmount =  BigDecimal.ZERO;

        for(OrderItemRequest itemRequest : createOrderRequest.getItems()){
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(()-> new ProductNotFoundException(itemRequest.getProductId()));

            if(product.getStatus() == ProductStatus.INACTIVE){
                throw new ProductIsNotActive(product.getId());
            } else if (product.getStockQuantity() == 0 || product.getStockQuantity() <itemRequest.getQuantity()) {
                throw new InsufficientStockException(product.getId());
            }

            product.setStockQuantity(
                    product.getStockQuantity() - itemRequest.getQuantity()
            );

            BigDecimal price = product.getPrice();

            BigDecimal subtotal = price.multiply(
                    BigDecimal.valueOf(itemRequest.getQuantity())
            );

            OrderItem orderItem = new OrderItem();

            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setPrice(price);
            orderItem.setSubtotal(subtotal);

            order.getItems().add(orderItem);
            totalAmount = totalAmount.add(subtotal);
        }

        order.setTotalAmount(totalAmount);
         Order savedOrder = orderRepository.save(order);

         return mapToResponse(savedOrder);
    }

    private CreateOrderResponse mapToResponse(Order savedOrder){

        CreateOrderResponse createOrderResponse = new CreateOrderResponse();
        createOrderResponse.setId(savedOrder.getId());
        createOrderResponse.setOrderNumber(savedOrder.getOrderNumber());
        createOrderResponse.setTotalAmount(savedOrder.getTotalAmount());
        createOrderResponse.setStatus(savedOrder.getStatus().name());

        List<OrderItemResponse> orderItems = savedOrder.getItems()
                .stream()
                .map(item -> {
                    OrderItemResponse orderItemResponse = new OrderItemResponse();
                    orderItemResponse.setProductId(item.getProduct().getId());
                    orderItemResponse.setQuantity(item.getQuantity());
                    orderItemResponse.setPrice(item.getPrice());
                    orderItemResponse.setSubtotal(item.getSubtotal());
                    return orderItemResponse;
                })
                .toList();

        createOrderResponse.setItems(orderItems);

        return createOrderResponse;

    }

    public CreateOrderResponse getOrderById(Long order_id){

        Order order = orderRepository.findOrderWithItems(order_id)
                .orElseThrow(()->  new RuntimeException("Order Not Found"));


       return mapToResponse(order);
    }

    public ResponseUpdatedOrder updateOrderStatus(Long order_id, OrderStatus requestedStatus){
        Order order = orderRepository.findById(order_id)
                .orElseThrow(()-> new OrderNotFountException(order_id));

        OrderStatus currentStatus = order.getStatus();
        if (!currentStatus.canTransitionTo(requestedStatus)) {
            throw new InvalidOrderStatusTransitionException(
                    currentStatus,
                    requestedStatus
            );
        }
        order.setStatus(requestedStatus);
        orderRepository.save(order);
        ResponseUpdatedOrder responseUpdatedOrder = new ResponseUpdatedOrder();
        responseUpdatedOrder.setId(order.getId());
        responseUpdatedOrder.setOrderNumber(order.getOrderNumber());
        responseUpdatedOrder.setStatus(order.getStatus());
        responseUpdatedOrder.setTotalAmount(order.getTotalAmount());
        return responseUpdatedOrder;
    }

}
