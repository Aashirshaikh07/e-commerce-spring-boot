package com.aashir.ecommerce.kafka;

import com.aashir.ecommerce.config.KafkaConsumerConfig;
import com.aashir.ecommerce.ECommerceApplication;
import com.aashir.ecommerce.config.KafkaErrorHandlerConfig;
import com.aashir.ecommerce.config.KafkaProducerConfig;
import com.aashir.ecommerce.entity.*;
import com.aashir.ecommerce.event.NotificationType;
import com.aashir.ecommerce.repository.*;
import com.aashir.ecommerce.service.*;
import com.aashir.payment.config.RazorpayProperties;
import com.aashir.payment.controller.RazorpayWebhookController;
import com.aashir.payment.entity.OutboxEvent;
import com.aashir.payment.entity.Payment;
import com.aashir.payment.entity.PaymentAttemptStatus;
import com.aashir.payment.entity.PaymentMethod;
import com.aashir.payment.entity.PaymentStatus;
import com.aashir.payment.entity.WebhookEventStatus;
import com.aashir.payment.gateway.RazorpayGateway;
import com.aashir.payment.gateway.RazorpayWebhookSignatureVerifier;
import com.aashir.payment.repository.PaymentAttemptRepository;
import com.aashir.payment.repository.PaymentRepository;
import com.aashir.payment.repository.WebhookEventRepository;
import com.aashir.payment.service.PaymentExpiryService;
import com.aashir.payment.service.WebhookEventProcessor;
import com.aashir.payment.service.WebhookPaymentReconciliationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=create-only")
@ContextConfiguration(classes = ECommerceApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentKafkaConsumer.class, KafkaConsumerConfig.class, KafkaProducerConfig.class, KafkaErrorHandlerConfig.class,
        OrderService.class, InventoryService.class, SimpleMeterRegistry.class, NotificationEventFactory.class,
        NotificationEventListener.class, NotificationPublisher.class})
@MockitoBean(types = {OutboxService.class, OrderKafkaProducer.class, OrderRequestFingerprint.class,
        OrderCreationTransactionService.class, OrderIdempotencyRecoveryService.class, CacheManager.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@EmbeddedKafka(topics = {"payment-failed", "payment-succeeded", PaymentKafkaHandoffTest.NOTIFICATION_TOPIC}, partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class PaymentKafkaHandoffTest {

    private static final BigDecimal AMOUNT = new BigDecimal("1598.00");
    private static final String WEBHOOK_SECRET = "payment-handoff-test-secret";
    static final String NOTIFICATION_TOPIC = "handoff-notifications";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8")
            .withDatabaseName("order_handoff")
            .withTmpFs(Map.of("/var/lib/mysql", "rw"));
    private static ConfigurableApplicationContext paymentContext;
    private static String bootstrapServers;

    @DynamicPropertySource
    static void orderProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.kafka.notification-topic", () -> NOTIFICATION_TOPIC);
    }

    @BeforeAll
    static void startPaymentContext(@Autowired EmbeddedKafkaBroker broker) throws Exception {
        bootstrapServers = broker.getBrokersAsString();
        var schema = MYSQL.execInContainer("mysql", "-uroot", "-p" + MYSQL.getPassword(), "-e",
                "CREATE DATABASE payment_handoff; GRANT ALL ON payment_handoff.* TO 'test'@'%'");
        assertEquals(0, schema.getExitCode(), schema::getStderr);
        paymentContext = new SpringApplicationBuilder(PaymentConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.config.name=payment-handoff",
                        "--spring.datasource.url=" + MYSQL.getJdbcUrl().replace("/order_handoff", "/payment_handoff"),
                        "--spring.datasource.username=" + MYSQL.getUsername(),
                        "--spring.datasource.password=" + MYSQL.getPassword(),
                        "--spring.jpa.hibernate.ddl-auto=create-only",
                        "--spring.kafka.bootstrap-servers=" + bootstrapServers,
                        "--spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                        "--spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer");
    }

    @AfterAll
    static void stopPaymentContext() {
        if (paymentContext != null) {
            paymentContext.close();
        }
    }

    @Autowired private OrderRepository orders;
    @Autowired private UserRepository users;
    @Autowired private ProductRepository products;
    @Autowired private InventoryRepository inventories;
    @Autowired private ProcessedEventRepository processedEvents;
    @Autowired private LatePaymentReviewRepository reviews;
    @Autowired private KafkaListenerEndpointRegistry listeners;
    @Autowired @Qualifier("dltKafkaTemplate") private KafkaTemplate<String, Object> notificationTemplate;

    @Test
    void expiryThenLateCaptureAndReplayCreateOneReview() throws Exception {
        Fixture fixture = createFixture();
        OutboxEvent failure = expire(fixture);
        publishPaymentOutbox();
        awaitProcessed(failure.getId());
        assertOrder(fixture, OrderStatus.CANCELLED, 5);

        OutboxEvent success = captureAndReplayWebhook(fixture);
        publishPaymentOutbox();
        awaitProcessed(success.getId());
        replayCaptureOverKafka(success);

        assertOrder(fixture, OrderStatus.CANCELLED, 5);
        var review = reviews.findById(success.getId()).orElseThrow();
        assertEquals(fixture.orderId(), review.getOrderId());
        assertEquals(fixture.paymentId(), review.getPaymentId());
        assertEquals(AMOUNT, review.getCapturedAmount());
        assertEquals(1, reviews.findAll().stream().filter(r -> r.getOrderId().equals(fixture.orderId())).count());
        assertNotifications(fixture, NotificationType.ORDER_CANCELLED);
    }

    @Test
    void captureDeliveredBeforeEarlierExpiryConfirmsOrderOnce() throws Exception {
        Fixture fixture = createFixture();
        MessageListenerContainer failureListener = listeners.getListenerContainers().stream()
                .filter(listener -> Arrays.asList(listener.getContainerProperties().getTopics()).contains("payment-failed"))
                .findFirst().orElseThrow();
        failureListener.pause();
        OutboxEvent failure;
        OutboxEvent success;
        try {
            await().until(failureListener::isContainerPaused);
            failure = expire(fixture);
            success = captureAndReplayWebhook(fixture);
            publishPaymentOutbox();
            awaitProcessed(success.getId());
            assertFalse(processedEvents.existsById(failure.getId()));
            assertOrder(fixture, OrderStatus.CONFIRMED, 4);
        } finally {
            failureListener.resume();
        }

        awaitProcessed(failure.getId());
        replayCaptureOverKafka(success);
        assertOrder(fixture, OrderStatus.CONFIRMED, 4);
        assertFalse(reviews.existsByOrderIdAndPaymentId(fixture.orderId(), fixture.paymentId()));
        assertNotifications(fixture, NotificationType.ORDER_CONFIRMED);
    }

    private Fixture createFixture() {
        String reference = UUID.randomUUID().toString();
        User customer = new User();
        customer.setName("Handoff customer");
        customer.setEmail(reference + "@test.local");
        customer.setPhone(reference);
        customer.setPassword("unused");
        customer.setRole("USER");
        customer.setStatus(UserStatus.ACTIVE);
        customer = users.save(customer);

        Product product = new Product();
        product.setProductName("Handoff product");
        product.setCategory("Test");
        product.setPrice(AMOUNT);
        product = products.save(product);
        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setQuantity(4);
        inventories.save(inventory);

        Order order = new Order();
        order.setOrderNumber(reference);
        order.setUser(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(AMOUNT);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(1);
        item.setPrice(AMOUNT);
        item.setSubtotal(AMOUNT);
        order.getItems().add(item);
        order = orders.save(order);

        Payment payment = Payment.create(order.getId(), customer.getId(), AMOUNT, "INR", reference);
        String gatewayOrderId = "order_" + reference;
        payment.startAttempt(PaymentMethod.UPI).attachGatewayOrder(gatewayOrderId);
        payment = paymentContext.getBean(PaymentRepository.class).save(payment);
        return new Fixture(order.getId(), product.getId(), payment.getId(), gatewayOrderId, customer.getEmail());
    }

    private OutboxEvent expire(Fixture fixture) throws Exception {
        var gateway = paymentContext.getBean(RazorpayGateway.class);
        when(gateway.fetchOrderPayments(fixture.gatewayOrderId())).thenReturn(List.of());
        assertTrue(paymentContext.getBean(PaymentExpiryService.class).expire(fixture.paymentId(), 30));
        assertEquals(PaymentStatus.FAILED, payment(fixture).getStatus());
        assertEquals(PaymentAttemptStatus.ABANDONED, attemptStatus(fixture));
        return paymentOutboxEvent(fixture, "PAYMENT_FAILED");
    }

    private OutboxEvent captureAndReplayWebhook(Fixture fixture) throws Exception {
        String gatewayPaymentId = "pay_" + fixture.paymentId();
        String body = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"%s","order_id":"%s"}}}}
                """.formatted(gatewayPaymentId, fixture.gatewayOrderId());
        var gateway = paymentContext.getBean(RazorpayGateway.class);
        var captured = new com.razorpay.Payment(new JSONObject()
                .put("id", gatewayPaymentId).put("order_id", fixture.gatewayOrderId())
                .put("amount", 159800).put("currency", "INR").put("status", "captured").put("method", "upi"));
        when(gateway.fetchPayment(gatewayPaymentId)).thenReturn(captured);
        when(gateway.toPaise(AMOUNT)).thenReturn(159800L);
        var webhook = paymentContext.getBean(RazorpayWebhookController.class);
        var processor = paymentContext.getBean(WebhookEventProcessor.class);
        String eventId = "evt_" + UUID.randomUUID();
        String signature = sign(body);
        assertEquals(Map.of("status", "accepted"), webhook.receiveWebhook(body, signature, eventId).getBody());
        processor.processPendingEvents();
        OutboxEvent success = paymentOutboxEvent(fixture, "PAYMENT_SUCCEEDED");

        assertEquals(Map.of("status", "duplicate"), webhook.receiveWebhook(body, signature, eventId).getBody());
        assertEquals(Map.of("status", "accepted"), webhook.receiveWebhook(body, signature, eventId + "-replay").getBody());
        processor.processPendingEvents();
        var webhooks = paymentContext.getBean(WebhookEventRepository.class);
        assertEquals(WebhookEventStatus.PROCESSED, webhooks.findById(eventId).orElseThrow().getStatus());
        assertEquals(WebhookEventStatus.PROCESSED, webhooks.findById(eventId + "-replay").orElseThrow().getStatus());
        assertEquals(success.getId(), paymentOutboxEvent(fixture, "PAYMENT_SUCCEEDED").getId());
        assertEquals(PaymentStatus.SUCCESS, payment(fixture).getStatus());
        assertEquals(gatewayPaymentId, payment(fixture).getTransactionId());
        assertEquals(PaymentAttemptStatus.SUCCESS, attemptStatus(fixture));
        return success;
    }

    private void replayCaptureOverKafka(OutboxEvent original) throws Exception {
        UUID replayId = UUID.randomUUID();
        try (var producer = new KafkaProducer<String, String>(
                Map.of("bootstrap.servers", bootstrapServers), new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(original.getTopic(), original.getMessageKey(), original.getPayload()))
                    .get(10, TimeUnit.SECONDS);
            var replay = JSON.readTree(original.getPayload()).deepCopy();
            ((tools.jackson.databind.node.ObjectNode) replay).put("eventId", replayId.toString());
            producer.send(new ProducerRecord<>(original.getTopic(), original.getMessageKey(), replay.toString()))
                    .get(10, TimeUnit.SECONDS);
        }
        awaitProcessed(replayId);
    }

    private void assertNotifications(Fixture fixture, NotificationType expected) {
        notificationTemplate.flush();
        try (var reader = new KafkaConsumer<String, String>(
                Map.of("bootstrap.servers", bootstrapServers, "enable.auto.commit", false),
                new StringDeserializer(), new StringDeserializer())) {
            var partitions = reader.partitionsFor(NOTIFICATION_TOPIC, Duration.ofSeconds(10)).stream()
                    .map(info -> new TopicPartition(info.topic(), info.partition())).toList();
            assertFalse(partitions.isEmpty());
            reader.assign(partitions);
            reader.seekToBeginning(partitions);
            var end = reader.endOffsets(partitions);
            var types = new ArrayList<String>();
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (partitions.stream().anyMatch(partition -> reader.position(partition) < end.get(partition))) {
                assertTrue(System.nanoTime() < deadline, "Timed out reading notification topic");
                for (var record : reader.poll(Duration.ofMillis(250))) {
                    if (fixture.email().equals(record.key())) {
                        types.add(JSON.readTree(record.value()).path("type").asText());
                    }
                }
            }
            assertEquals(List.of(expected.name()), types);
        }
    }

    private void publishPaymentOutbox() {
        paymentContext.getBean(com.aashir.payment.service.OutboxPublisher.class).publishPendingEvents();
    }

    private OutboxEvent paymentOutboxEvent(Fixture fixture, String type) {
        var matches = paymentContext.getBean(com.aashir.payment.repository.OutboxEventRepository.class).findAll().stream()
                .filter(event -> event.getEventType().equals(type) && event.getMessageKey().equals(fixture.orderId().toString()))
                .toList();
        assertEquals(1, matches.size());
        return matches.getFirst();
    }

    private Payment payment(Fixture fixture) {
        return paymentContext.getBean(PaymentRepository.class).findById(fixture.paymentId()).orElseThrow();
    }

    private PaymentAttemptStatus attemptStatus(Fixture fixture) {
        return paymentContext.getBean(PaymentAttemptRepository.class)
                .findByGatewayOrderId(fixture.gatewayOrderId()).orElseThrow().getStatus();
    }

    private void assertOrder(Fixture fixture, OrderStatus status, int stock) {
        assertEquals(status, orders.findById(fixture.orderId()).orElseThrow().getStatus());
        assertEquals(stock, inventories.findByProductId(fixture.productId()).orElseThrow().getQuantity());
    }

    private void awaitProcessed(UUID eventId) {
        await().until(() -> processedEvents.existsById(eventId));
    }

    private static org.awaitility.core.ConditionFactory await() {
        return Awaitility.await().atMost(Duration.ofSeconds(20));
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private record Fixture(Long orderId, Long productId, Long paymentId, String gatewayOrderId, String email) {}

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Payment.class)
    @EnableJpaRepositories(basePackageClasses = PaymentRepository.class)
    @Import({PaymentExpiryService.class, RazorpayWebhookController.class, RazorpayWebhookSignatureVerifier.class,
            WebhookEventProcessor.class, WebhookPaymentReconciliationService.class,
            com.aashir.payment.service.OutboxService.class, com.aashir.payment.service.OutboxPublisher.class})
    static class PaymentConfiguration {
        @Bean
        RazorpayGateway gateway() {
            return mock(RazorpayGateway.class);
        }

        @Bean
        RazorpayProperties razorpayProperties() {
            return new RazorpayProperties("test-key", "test-secret", WEBHOOK_SECRET);
        }
    }
}
