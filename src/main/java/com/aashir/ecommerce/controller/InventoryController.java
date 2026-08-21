package com.aashir.ecommerce.controller;

import com.aashir.ecommerce.dto.InventoryResponse;
import com.aashir.ecommerce.dto.UpdateStockRequest;
import com.aashir.ecommerce.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/{productId}")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryResponse createInventory(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateStockRequest request) {

        return inventoryService.createInventory(productId, request);
    }

    @GetMapping("/{productId}")
    public InventoryResponse getInventory(
            @PathVariable Long productId) {

        return inventoryService.getInventory(productId);
    }

    @PatchMapping("/{productId}/add")
    public InventoryResponse addStock(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateStockRequest request) {

        return inventoryService.addStock(productId, request);
    }

    @PatchMapping("/{productId}/remove")
    public InventoryResponse removeStock(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateStockRequest request) {

        return inventoryService.removeStock(productId, request);
    }
}