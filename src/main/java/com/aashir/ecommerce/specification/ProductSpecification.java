package com.aashir.ecommerce.specification;

import com.aashir.ecommerce.entity.Product;
import com.aashir.ecommerce.entity.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Locale;

public class ProductSpecification {
    private ProductSpecification() {
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, cb) ->
                cb.equal(root.get("status"), status);
    }

    public static Specification<Product> hasCategory(String category) {
        return (root, query, cb) ->
                cb.equal(
                        cb.lower(root.get("category")),
                        category.trim().toLowerCase(Locale.ROOT)
                );
    }

    public static Specification<Product> priceGreaterThanOrEqualTo(
            BigDecimal minPrice) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> priceLessThanOrEqualTo(
            BigDecimal maxPrice) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> keywordContains(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";

            return cb.or(
                    cb.like(cb.lower(root.get("productName")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }
}
