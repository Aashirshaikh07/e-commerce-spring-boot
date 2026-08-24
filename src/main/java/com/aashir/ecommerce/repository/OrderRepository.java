package com.aashir.ecommerce.repository;

import com.aashir.ecommerce.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order,Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findById(Long id);

    @Query("""
    SELECT o
    FROM Order o
    LEFT JOIN FETCH o.items
    WHERE o.id = :id
""")
    Optional<Order> findOrderWithItems(Long id);

    Optional<Order> findByIdAndUserId(Long orderId,Long userId);



}
