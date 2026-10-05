package com.aashir.ecommerce.repository;

import com.aashir.ecommerce.entity.ProcessEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessEvent, UUID> {

}
