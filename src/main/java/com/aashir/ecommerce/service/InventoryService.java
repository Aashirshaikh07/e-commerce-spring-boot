package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.InventoryResponse;
import com.aashir.ecommerce.dto.UpdateStockRequest;
import com.aashir.ecommerce.entity.Inventory;
import com.aashir.ecommerce.entity.Product;
import com.aashir.ecommerce.exception.InventoryNotFoundException;
import com.aashir.ecommerce.exception.ProductNotFoundException;
import com.aashir.ecommerce.repository.InventoryRepository;
import com.aashir.ecommerce.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.hibernate.sql.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    public InventoryResponse getInventory(Long productId){
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(()-> new InventoryNotFoundException(productId));

        return mapToResponse(inventory);

    }

    @Transactional
    public InventoryResponse createInventory(Long prductId, UpdateStockRequest request){
        Product product = productRepository.findById(prductId)
                .orElseThrow(()->new ProductNotFoundException(prductId));

        if(inventoryRepository.findByProductId(product.getId()).isPresent()){
            throw new IllegalArgumentException("Product already exists");
        }

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setQuantity(request.quantity());

        return mapToResponse(inventoryRepository.save(inventory));
    }

    @Transactional
    public InventoryResponse addStock(Long prductId, UpdateStockRequest request){
        Inventory inventory = inventoryRepository.findByProductId(prductId)
                .orElseThrow(()->new InventoryNotFoundException(prductId));

        inventory.setQuantity(inventory.getQuantity() + request.quantity());

        return mapToResponse(inventoryRepository.save(inventory));
    }

    @Transactional
    public InventoryResponse removeStock(Long productId, UpdateStockRequest request) {

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        if (inventory.getQuantity() < request.quantity()) {
            throw new IllegalStateException("Insufficient stock");
        }

        inventory.setQuantity(inventory.getQuantity() - request.quantity());

        return mapToResponse(inventoryRepository.save(inventory));
    }


    private InventoryResponse mapToResponse(Inventory inventory){
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProduct().getId(),
                inventory.getQuantity(),
                inventory.getCreationAt(),
                inventory.getUpdateAt()
        );
    }
}
