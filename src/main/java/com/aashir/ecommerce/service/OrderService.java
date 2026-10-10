package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.*;
import com.aashir.ecommerce.entity.*;
import com.aashir.ecommerce.event.OrderCancelledEvent;
import com.aashir.ecommerce.event.OrderCreatedEvent;
import com.aashir.ecommerce.event.OrderCreatedKafkaEvent;
import com.aashir.ecommerce.event.OrderItemEvent;
import com.aashir.ecommerce.exception.*;
import com.aashir.ecommerce.kafka.OrderKafkaProducer;
import com.aashir.ecommerce.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.aashir.ecommerce.security.SecurityUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;


@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxService outboxService;
    private final OrderIdempotencyRepository orderIdempotencyRepository;
    private final OrderRequestFingerprint orderRequestFingerprint;
    private final OrderCreationTransactionService orderCreationTransactionService;
    private final OrderIdempotencyRecoveryService orderIdempotencyRecoveryService;
    private static final Logger log =
            LoggerFactory.getLogger(OrderService.class);

    private final MeterRegistry meterRegistry;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository, OrderItemRepository orderItemRepository, InventoryRepository inventoryRepository, InventoryService inventoryService, UserRepository userRepository, ApplicationEventPublisher eventPublisher, OrderKafkaProducer orderKafkaProducer, OutboxService outboxService, OrderIdempotencyRepository orderIdempotencyRepository, OrderRequestFingerprint orderRequestFingerprint, OrderCreationTransactionService orderCreationTransactionService, OrderIdempotencyRecoveryService orderIdempotencyRecoveryService, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.outboxService = outboxService;
        this.orderIdempotencyRepository = orderIdempotencyRepository;
        this.orderRequestFingerprint = orderRequestFingerprint;
        this.orderCreationTransactionService = orderCreationTransactionService;
        this.orderIdempotencyRecoveryService = orderIdempotencyRecoveryService;
        this.meterRegistry = meterRegistry;
    }


    public OrderCreationResult  createOrder(CreateOrderRequest createOrderRequest,String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);

        User user = SecurityUtils.getCurrentUser();

        String fingerprint = orderRequestFingerprint.calculate(createOrderRequest);

        var existingRecord = orderIdempotencyRepository.findWithOrderByUserIdAndIdempotencyKey(
                user.getId(),
                idempotencyKey
        );

        if (existingRecord.isPresent()) {
            OrderIdempotency record = existingRecord.get();

            if (!record.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException("Idempotency key has already been used with a different request");
            }

            if (record.getOrder() == null) {
                throw new IllegalStateException(
                        "Idempotency record exists without an associated order"
                );
            }
                log.info(
                        "Returning existing order for idempotency key, orderId={}",
                        record.getOrder().getId()
                );
            return new OrderCreationResult(
                    mapToResponse(record.getOrder()),
                    true
             );
            }
        try {
            CreateOrderResponse response = orderCreationTransactionService.createOrder(
                    createOrderRequest,
                    user,
                    idempotencyKey,
                    fingerprint
            );
            return new OrderCreationResult(response,false);
        }catch (DataIntegrityViolationException ex) {
            var recoveredRecord = orderIdempotencyRecoveryService.findExisting(
                    user.getId(),
                    idempotencyKey
            );
            if(recoveredRecord.isEmpty()){
                throw ex;
            }

            OrderIdempotency record = recoveredRecord.get();
            if (!record.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException(
                        "Idempotency key has already been used with a different request"
                );
            }
            if (record.getOrder() == null) {
                throw ex;
            }
            log.info("Recovered concurrent order request, orderId={}",
                    record.getOrder().getId());

            return new OrderCreationResult(
                    mapToResponse(record.getOrder()),
                    true
            );
        }

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

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must not be blank"
            );
        }

        if (idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must not exceed 100 characters"
            );
        }
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

    public void markPaymentSuccessful(Order order){

        if(!order.getStatus().canTransitionTo(OrderStatus.CONFIRMED)){
            throw new IllegalStateException(
                    "Order cannot be confirmed from status " + order.getStatus()
            );
        }
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("Payment succeeded for orderId ={}",order.getId());
    }

  @Transactional
    public void cancelOrder(Order order,String reason){

      log.info(
              "Cancelling orderId={} because {}",
              order.getId(),
              reason
      );

      updateOrderStatus(order.getId(), OrderStatus.CANCELLED);
  }

}
