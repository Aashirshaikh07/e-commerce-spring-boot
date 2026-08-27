package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.*;
import com.aashir.ecommerce.entity.*;
import com.aashir.ecommerce.event.OrderCancelledEvent;
import com.aashir.ecommerce.event.OrderCreatedEvent;
import com.aashir.ecommerce.event.OrderItemEvent;
import com.aashir.ecommerce.exception.*;
import com.aashir.ecommerce.repository.*;
import com.aashir.ecommerce.security.SecurityUtils;
import org.springframework.context.ApplicationEventPublisher;
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
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository, OrderItemRepository orderItemRepository, InventoryRepository inventoryRepository, InventoryService inventoryService, UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest) {
        String email = SecurityUtils.getCurrentUserEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("User not found"));

        Order order = new Order();

        order.setOrderNumber("ORD-"+ UUID.randomUUID());
        order.setStatus(OrderStatus.PENDING);
        order.setUser(user);

        BigDecimal totalAmount =  BigDecimal.ZERO;

        for(OrderItemRequest itemRequest : createOrderRequest.getItems()){
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(()-> new ProductNotFoundException(itemRequest.getProductId()));

            if(product.getStatus() == ProductStatus.INACTIVE){
                throw new ProductIsNotActive(product.getId());
            }
            Inventory inventory = inventoryRepository.findByProductId(product.getId())
                    .orElseThrow(()-> new InventoryNotFoundException(product.getId()));

            if(inventory.getQuantity() < itemRequest.getQuantity()){
                throw new InsufficientStockException(product.getId());
            }
            inventory.setQuantity(
                    inventory.getQuantity() - itemRequest.getQuantity()
            );

            inventoryRepository.save(inventory);

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

         OrderCreatedEvent event = new OrderCreatedEvent(
                 user.getName(),
                 user.getEmail(),
                 savedOrder.getOrderNumber(),
                 savedOrder.getStatus(),
                 savedOrder.getItems()
                         .stream()
                         .map(item->new OrderItemEvent(
                                 item.getProduct().getProductName(),
                                 item.getQuantity(),
                                 item.getPrice(),
                                 item.getSubtotal()
                         ))
                         .toList(),
                 savedOrder.getTotalAmount()
         );

         eventPublisher.publishEvent(event);

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

        String email = SecurityUtils.getCurrentUserEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("User not found"));

        Order order = orderRepository.findByIdAndUserId(order_id,user.getId())
                .orElseThrow(()->  new RuntimeException("Order Not Found"));


       return mapToResponse(order);
    }


    @Transactional
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
        OrderStatus orderStatus =order.getStatus();
        if (requestedStatus == OrderStatus.CANCELLED) {
            restoreInventory(order);
        }

        orderRepository.save(order);

       if (requestedStatus == OrderStatus.CANCELLED) {
           publishOrderCancelledEvent(order);
       }
        return mapToUpdatedOrderResponse(order);
    }

    //For Next all orders fetech
//    String email = SecurityUtils.getCurrentUserEmail();
//
//    User user = userRepository.findUserWithOrdersByEmail(email)
//            .orElseThrow(() -> new UserNotFoundException(email));
//
//    List<Order> orders = user.getOrders();



   private void publishOrderCancelledEvent(Order order){

        OrderCancelledEvent event = new  OrderCancelledEvent(
                order.getUser().getName(),
                order.getUser().getEmail(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getItems().stream()
                        .map(item->new OrderItemEvent(
                                item.getProduct().getProductName(),
                                item.getQuantity(),
                                item.getPrice(),
                                item.getSubtotal()
                        ))
                        .toList(),
                order.getTotalAmount()
        );
        eventPublisher.publishEvent(event);
   }

    private ResponseUpdatedOrder mapToUpdatedOrderResponse(Order order) {

        ResponseUpdatedOrder response = new ResponseUpdatedOrder();

        response.setId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        response.setStatus(order.getStatus());
        response.setTotalAmount(order.getTotalAmount());

        return response;
    }

    private void restoreInventory(Order order){
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

        for (OrderItem orderItem : orderItems) {
            Long productId = orderItem.getProduct().getId();
            Integer quantity = orderItem.getQuantity();

            inventoryService.addStock(productId, new UpdateStockRequest(quantity));
        }
    }

}
