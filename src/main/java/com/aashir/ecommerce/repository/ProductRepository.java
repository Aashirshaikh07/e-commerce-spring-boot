package com.aashir.ecommerce.repository;

import com.aashir.ecommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product,Long> {

    List<Product> findAllById(long id);

    Optional<Product> findById(Long id);
}
