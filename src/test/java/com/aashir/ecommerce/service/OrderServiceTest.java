package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.CreateOrderRequest;
import com.aashir.ecommerce.dto.OrderItemRequest;
import com.aashir.ecommerce.entity.*;
import com.aashir.ecommerce.repository.*;
import com.aashir.ecommerce.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_shouldPublishOrderCreatedEvent() {

        User user = new User();
        user.setId(1L);
        user.setName("Ashir");
        user.setEmail("ashir@gmail.com");

        Product product = new Product();
        product.setId(1L);
        product.setProductName("Laptop");
        product.setPrice(new BigDecimal("89999"));
        product.setStatus(ProductStatus.ACTIVE);

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setQuantity(10);

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(1L);
        itemRequest.setQuantity(1);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(itemRequest));

        Order savedOrder = new Order();
        savedOrder.setId(1L);
        savedOrder.setOrderNumber("ORD-123");
        savedOrder.setStatus(OrderStatus.PENDING);
        savedOrder.setUser(user);
        savedOrder.setTotalAmount(new BigDecimal("89999"));

        OrderItem orderItem = new OrderItem();
        orderItem.setProduct(product);
        orderItem.setQuantity(1);
        orderItem.setPrice(new BigDecimal("89999"));
        orderItem.setSubtotal(new BigDecimal("89999"));

        savedOrder.getItems().add(orderItem);

        SecurityUtils mockSecurityUtils = null;
    }
}
