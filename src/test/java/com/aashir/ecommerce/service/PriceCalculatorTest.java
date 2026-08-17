package com.aashir.ecommerce.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PriceCalculatorTest {
    @Test
    void shouldCalculateFinalPriceWithDiscount(){
        PriceCalculator priceCalculator = new PriceCalculator();

        BigDecimal result = priceCalculator.calculateFinalPrice(
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(10)
        );
        assertEquals(BigDecimal.valueOf(900).setScale(2), result);
    }

    @Test
    void shouldRejectNegativePrice() {

        PriceCalculator calculator = new PriceCalculator();

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateFinalPrice(
                        BigDecimal.valueOf(-100),
                        BigDecimal.valueOf(10)
                )
        );
    }
}

