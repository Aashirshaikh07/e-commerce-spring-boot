package com.aashir.ecommerce.repository;

import com.aashir.ecommerce.entity.OrderIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderIdempotencyRepository
        extends JpaRepository<OrderIdempotency, Long> {
    @Query("""
            SELECT DISTINCT oi
            FROM OrderIdempotency oi
            LEFT JOIN FETCH oi.order o
            LEFT JOIN FETCH o.items
            WHERE oi.user.id = :userId
              AND oi.idempotencyKey = :idempotencyKey
            """)
    Optional<OrderIdempotency> findWithOrderByUserIdAndIdempotencyKey(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}