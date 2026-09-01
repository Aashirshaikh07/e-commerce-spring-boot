package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.*;
import com.aashir.ecommerce.entity.*;
import com.aashir.ecommerce.event.OrderCancelledEvent;
import com.aashir.ecommerce.event.OrderCreatedEvent;
import com.aashir.ecommerce.event.OrderItemEvent;
import com.aashir.ecommerce.exception.*;
import com.aashir.ecommerce.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.aashir.ecommerce.security.SecurityUtils;
import org.springframework.context.ApplicationEventPublisher;
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

    private static final Logger log =
            LoggerFactory.getLogger(OrderService.class);

    private final MeterRegistry meterRegistry;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository, OrderItemRepository orderItemRepository, InventoryRepository inventoryRepository, InventoryService inventoryService, UserRepository userRepository, ApplicationEventPublisher eventPublisher, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest) {
        String email = SecurityUtils.getCurrentUserEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("User not found"));

        log.info("Creating new order with email ={} and item={}",email,+createOrderRequest.getItems().size());
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

        meterRegistry.counter("orders.created").increment();

        log.info("Order created successfully: orderId={}", order.getId());

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

        log.info("Updating orderStatus with orderId ={}",order.getId());

        OrderStatus currentStatus = order.getStatus();
        if (!currentStatus.canTransitionTo(requestedStatus)) {
            log.info("Order Status Transition can't be updated with wrong wrong steps");
            throw new InvalidOrderStatusTransitionException(
                    currentStatus,
                    requestedStatus
            );
        }
        order.setStatus(requestedStatus);
        OrderStatus orderStatus =order.getStatus();
        if (requestedStatus == OrderStatus.CANCELLED) {
            restoreInventory(order);
            log.info("cancelled orderId ={}",order.getId());
            meterRegistry.counter("orders.cancelled").increment();
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
            log.info("Restore inventory successfully with inventoryProductName={} and productCount",orderItem.getProduct().getProductName(),orderItem.getQuantity());

        }
    }

}
