package com.aashir.ecommerce.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpGeneratorTest {

    private final OtpGenerator generator = new OtpGenerator();

    @Test
    void generatesOnlyDigitsOfTheRequestedLength() {
        for (int i = 0; i < 2000; i++) {
            String otp = generator.generate(6);
            assertEquals(6, otp.length());
            assertTrue(otp.matches("\\d{6}"));
        }
    }
}