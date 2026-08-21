package com.aashir.ecommerce.repository;

import com.aashir.ecommerce.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem,Long> {


    List<OrderItem> findByOrderId(Long Order_id);
}
