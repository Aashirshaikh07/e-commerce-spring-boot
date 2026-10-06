package com.aashir.ecommerce.config;

import com.aashir.ecommerce.event.PaymentFailedEvent;
import com.aashir.ecommerce.event.PaymentSucceededEvent;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @PostConstruct
    void init() {
        System.out.println(">>> PaymentKafkaConsumer bean created");
    }
    private Map<String, Object> consumerProps() {
        Map<String, Object> config = new HashMap<>();

        config.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092"
        );
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return config;
    }


    @Bean
    public ConsumerFactory<String, PaymentSucceededEvent> paymentSucceededConsumerFactory() {
        JacksonJsonDeserializer<PaymentSucceededEvent> deserializer = new JacksonJsonDeserializer<>(PaymentSucceededEvent.class,false);

        return new DefaultKafkaConsumerFactory<>(consumerProps(), new StringDeserializer(), new ErrorHandlingDeserializer<>(deserializer));

    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentSucceededEvent> paymentSucceededKafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentSucceededEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentSucceededConsumerFactory());
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, PaymentFailedEvent> paymentFailedConsumerFactory() {
        JacksonJsonDeserializer<PaymentFailedEvent> deserializer = new JacksonJsonDeserializer<>(PaymentFailedEvent.class,false);

        return new DefaultKafkaConsumerFactory<>(
                consumerProps(),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializer)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> paymentFailedKafkaListenerContainerFactory(DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentFailedConsumerFactory());
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

}