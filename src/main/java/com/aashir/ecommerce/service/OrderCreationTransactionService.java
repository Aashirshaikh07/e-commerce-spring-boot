package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.CreateOrderRequest;
import com.aashir.ecommerce.dto.CreateOrderResponse;
import com.aashir.ecommerce.dto.OrderItemRequest;
import com.aashir.ecommerce.dto.OrderItemResponse;
import com.aashir.ecommerce.entity.*;
import com.aashir.ecommerce.event.OrderCreatedEvent;
import com.aashir.ecommerce.event.OrderCreatedKafkaEvent;
import com.aashir.ecommerce.event.OrderItemEvent;
import com.aashir.ecommerce.exception.InsufficientStockException;
import com.aashir.ecommerce.exception.InventoryNotFoundException;
import com.aashir.ecommerce.exception.ProductIsNotActive;
import com.aashir.ecommerce.exception.ProductNotFoundException;
import com.aashir.ecommerce.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class OrderCreationTransactionService {
    private static final Logger log =
            LoggerFactory.getLogger(OrderCreationTransactionService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final OrderIdempotencyRepository orderIdempotencyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxService outboxService;
    private final MeterRegistry meterRegistry;

    public OrderCreationTransactionService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            UserRepository userRepository,
            OrderIdempotencyRepository orderIdempotencyRepository,
            ApplicationEventPublisher eventPublisher,
            OutboxService outboxService,
            MeterRegistry meterRegistry
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
        this.orderIdempotencyRepository = orderIdempotencyRepository;
        this.eventPublisher = eventPublisher;
        this.outboxService = outboxService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest createOrderRequest,User user,String idempotencyKey,String requestFingerprint) {
        OrderIdempotency idempotency = new OrderIdempotency(
                user,
                idempotencyKey,
                requestFingerprint
        );

        orderIdempotencyRepository.saveAndFlush(idempotency);

        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID());
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

        idempotency.setOrder(savedOrder);
        orderIdempotencyRepository.save(idempotency);

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
        OrderCreatedKafkaEvent kafkaEvent = new OrderCreatedKafkaEvent(
                UUID.randomUUID(),
                savedOrder.getId(),
                savedOrder.getOrderNumber(),
                user.getId(),
                savedOrder.getTotalAmount()
        );
        outboxService.saveEvent(
                kafkaEvent.eventId(),
                "OrderCreatedKafkaEvent",
                "order-events",
                kafkaEvent
        );
        //orderKafkaProducer.publishOrderCreated(kafkaEvent);


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

}
