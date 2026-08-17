package com.aashir.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PriceCalculator {

    public BigDecimal calculateFinalPrice(BigDecimal price, BigDecimal discountPercentage) {

        if (price == null || discountPercentage == null) {
            throw new IllegalArgumentException("Price and discount percentage cannot be null.");

        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }

        if (discountPercentage.compareTo(BigDecimal.ZERO) < 0 ||
                discountPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "Discount must be between 0 and 100"
            );
        }

        BigDecimal discount = price
                .multiply(discountPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return price.subtract(discount);
    }
}
