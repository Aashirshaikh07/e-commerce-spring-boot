package com.aashir.ecommerce.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class OtpGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate(int length) {
        int bound = (int) Math.pow(10, length);
        int value = secureRandom.nextInt(bound);
        return String.format("%0" + length + "d", value);
    }
}
