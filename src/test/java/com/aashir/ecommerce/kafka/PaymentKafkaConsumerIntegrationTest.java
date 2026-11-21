package com.aashir.ecommerce.kafka;

import com.aashir.ecommerce.entity.Order;
import com.aashir.ecommerce.entity.OrderStatus;
import com.aashir.ecommerce.entity.User;
import com.aashir.ecommerce.entity.UserStatus;
import com.aashir.ecommerce.event.PaymentSucceededEvent;
import com.aashir.ecommerce.repository.LatePaymentReviewRepository;
import com.aashir.ecommerce.repository.OrderRepository;
import com.aashir.ecommerce.repository.ProcessedEventRepository;
import com.aashir.ecommerce.repository.UserRepository;
import com.aashir.ecommerce.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=create-only")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PaymentKafkaConsumer.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PaymentKafkaConsumerIntegrationTest {

    private static final BigDecimal CAPTURED_AMOUNT = new BigDecimal("1598.00");

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private PaymentKafkaConsumer consumer;
    @Autowired
    private LatePaymentReviewRepository reviews;
    @Autowired
    private ProcessedEventRepository processedEvents;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private CacheManager cacheManager;

    @BeforeEach
    void clearEventTables() {
        reviews.deleteAllInBatch();
        processedEvents.deleteAllInBatch();
    }

    @Test
    void recordsCaptureForCancelledOrderAndMarksEventProcessed() {
        PaymentSucceededEvent event = captureForCancelledOrder();

        consumer.handlePaymentSucceeded(event);
        consumer.handlePaymentSucceeded(event);

        assertReviewRecorded(event);
        assertEquals(1, reviews.count());
    }

    @Test
    void failedReviewWriteLeavesEventEligibleForRetry() {
        PaymentSucceededEvent event = captureForCancelledOrder();
        jdbc.execute("""
                ALTER TABLE late_payment_reviews ADD CONSTRAINT test_reject_review
                CHECK (captured_amount <> 1598.00)
                """);
        try {
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> consumer.handlePaymentSucceeded(event));
            assertTrue(NestedExceptionUtils.getMostSpecificCause(failure).getMessage()
                    .contains("test_reject_review"), failure::toString);
            assertFalse(reviews.existsById(event.eventId()));
            assertFalse(processedEvents.existsById(event.eventId()));
        } finally {
            jdbc.execute("ALTER TABLE late_payment_reviews DROP CHECK test_reject_review");
        }

        consumer.handlePaymentSucceeded(event);

        assertReviewRecorded(event);
    }

    @Test
    void failedProcessedEventWriteRollsBackReview() {
        PaymentSucceededEvent event = captureForCancelledOrder();
        jdbc.execute("""
                ALTER TABLE processed_events ADD CONSTRAINT test_reject_processed
                CHECK (processed_at < '2000-01-01')
                """);
        try {
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> consumer.handlePaymentSucceeded(event));
            assertTrue(NestedExceptionUtils.getMostSpecificCause(failure).getMessage()
                    .contains("test_reject_processed"), failure::toString);
            assertFalse(reviews.existsById(event.eventId()));
            assertFalse(processedEvents.existsById(event.eventId()));
        } finally {
            jdbc.execute("ALTER TABLE processed_events DROP CHECK test_reject_processed");
        }

        consumer.handlePaymentSucceeded(event);

        assertReviewRecorded(event);
    }

    private PaymentSucceededEvent captureForCancelledOrder() {
        String reference = UUID.randomUUID().toString();
        User user = new User();
        user.setName("Test Customer");
        user.setEmail(reference + "@test.local");
        user.setPhone(reference);
        user.setPassword("unused");
        user.setRole("USER");
        user.setStatus(UserStatus.ACTIVE);

        Order order = new Order();
        order.setUser(users.save(user));
        order.setOrderNumber(reference);
        order.setTotalAmount(CAPTURED_AMOUNT);
        order.setStatus(OrderStatus.CANCELLED);
        return new PaymentSucceededEvent(UUID.randomUUID(), 10L, orders.save(order).getId(), CAPTURED_AMOUNT);
    }

    private void assertReviewRecorded(PaymentSucceededEvent event) {
        var review = reviews.findById(event.eventId()).orElseThrow();
        assertEquals(event.orderId(), review.getOrderId());
        assertEquals(event.paymentId(), review.getPaymentId());
        assertEquals(event.amount(), review.getCapturedAmount());
        assertNotNull(review.getCreatedAt());
        assertTrue(processedEvents.existsById(event.eventId()));
        assertEquals(OrderStatus.CANCELLED, orders.findById(event.orderId()).orElseThrow().getStatus());
        verifyNoInteractions(orderService);
    }
}
