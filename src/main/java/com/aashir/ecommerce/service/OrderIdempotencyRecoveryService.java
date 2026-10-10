package com.aashir.ecommerce.service;

import com.aashir.ecommerce.entity.OrderIdempotency;
import com.aashir.ecommerce.repository.OrderIdempotencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OrderIdempotencyRecoveryService {

    private final OrderIdempotencyRepository orderIdempotencyRepository;

    public OrderIdempotencyRecoveryService(
            OrderIdempotencyRepository orderIdempotencyRepository) {
        this.orderIdempotencyRepository = orderIdempotencyRepository;
    }

    @Transactional(readOnly = true)
    public Optional<OrderIdempotency> findExisting(
            Long userId,
            String idempotencyKey) {

        return orderIdempotencyRepository
                .findWithOrderByUserIdAndIdempotencyKey(
                        userId,
                        idempotencyKey
                );
    }
}