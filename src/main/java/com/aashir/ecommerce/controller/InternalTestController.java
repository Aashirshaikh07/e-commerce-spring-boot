package com.aashir.ecommerce.controller;

import com.aashir.ecommerce.service.PaymentServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal-test")
@RequiredArgsConstructor
public class InternalTestController {

    private final PaymentServiceClient paymentServiceClient;

    @PostMapping("/payment")
    public ResponseEntity<String> testPaymentService() {

        String response = paymentServiceClient.processPayment();

        return ResponseEntity.ok(response);
    }
}
