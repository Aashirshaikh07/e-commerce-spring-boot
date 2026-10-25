package com.aashir.ecommerce.service;
import com.aashir.ecommerce.entity.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProductMixingService {

    private static final List<String> CATEGORY_ORDER = List.of(
            "electronics",
            "clothes",
            "beauty",
            "fashion",
            "medicine"
    );

    public List<Product> mixByCategory(List<Product> products) {
        Map<String, List<Product>> productsByCategory =
                new LinkedHashMap<>();

        for (Product product : products) {
            String category = product.getCategory() == null
                    ? ""
                    : product.getCategory().trim().toLowerCase(Locale.ROOT);

            productsByCategory
                    .computeIfAbsent(category, key -> new ArrayList<>())
                    .add(product);
        }

        Map<String, Integer> categoryIndexes = new HashMap<>();
        List<Product> mixedProducts = new ArrayList<>();

        boolean productsRemaining;

        do {
            productsRemaining = false;

            // Process the preferred categories first.
            for (String category : CATEGORY_ORDER) {
                List<Product> categoryProducts =
                        productsByCategory.get(category);

                if (categoryProducts == null) {
                    continue;
                }

                int index = categoryIndexes.getOrDefault(category, 0);

                if (index < categoryProducts.size()) {
                    mixedProducts.add(categoryProducts.get(index));
                    categoryIndexes.put(category, index + 1);
                    productsRemaining = true;
                }
            }

            // Also support categories added in the future.
            for (Map.Entry<String, List<Product>> entry
                    : productsByCategory.entrySet()) {

                String category = entry.getKey();

                if (CATEGORY_ORDER.contains(category)) {
                    continue;
                }

                int index = categoryIndexes.getOrDefault(category, 0);

                if (index < entry.getValue().size()) {
                    mixedProducts.add(entry.getValue().get(index));
                    categoryIndexes.put(category, index + 1);
                    productsRemaining = true;
                }
            }

        } while (productsRemaining);

        return mixedProducts;
    }
}
