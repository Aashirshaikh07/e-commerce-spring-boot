package com.aashir.ecommerce.repository;

import com.aashir.ecommerce.entity.LatePaymentReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LatePaymentReviewRepository extends JpaRepository<LatePaymentReview, UUID> {
}
