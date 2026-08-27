package com.aashir.ecommerce.repository;

import com.aashir.ecommerce.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductId(long productId);


    List<Inventory> findByQuantityLessThanEqual(Integer quantity);
}
