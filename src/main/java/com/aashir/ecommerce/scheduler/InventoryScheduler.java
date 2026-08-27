package com.aashir.ecommerce.scheduler;

import com.aashir.ecommerce.entity.Inventory;
import com.aashir.ecommerce.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryScheduler {

    private final InventoryRepository inventoryRepository;

    @Value("${inventory.low-stock.inventoryWarn}")
    private int inventoryWarn;

    Set<Long> prodcutIds = new HashSet<>();

    @Scheduled(fixedRate = 20000)
    public void checkInventory()
    {
        List<Inventory> inventoryListLowStock = inventoryRepository.findByQuantityLessThanEqual(inventoryWarn);

        for(Inventory inventory : inventoryListLowStock){
            Long productId = inventory.getProduct().getId();
            Integer quantity = inventory.getQuantity();

            if(quantity <= inventoryWarn){
                if(!prodcutIds.contains(productId)){
                    prodcutIds.add(productId);

                    log.warn(
                            "LOW STOCK ALERT: productId={},productName={},quantity={}",
                            inventory.getProduct().getId(),
                            inventory.getProduct().getProductName(),
                            inventory.getQuantity()
                    );
                }
            }else {
                prodcutIds.remove(productId);
            }
        }

    }
}
