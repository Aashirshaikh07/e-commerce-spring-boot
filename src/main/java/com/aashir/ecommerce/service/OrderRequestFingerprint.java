package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.CreateOrderRequest;
import com.aashir.ecommerce.dto.OrderItemRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Component
public class OrderRequestFingerprint {

    public String calculate(CreateOrderRequest request) {
        List<OrderItemRequest> items = request.getItems();

        String canonicalRequest = items.stream()
                .sorted(Comparator.comparing(OrderItemRequest::getProductId))
                .map(item -> item.getProductId() + ":" + item.getQuantity())
                .reduce((first, second) -> first + "|" + second)
                .orElse("");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    canonicalRequest.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }
}