package com.aashir.ecommerce.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
public class PaymentServiceClient {

    private final RestTemplate restTemplate;

    @Value("${service.payment.url}")
    private String paymenServicetUrl;

    @Value("${service.security.secret}")
    private String serviceSecuritySecret;

    public String processPayment(){
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Secret",serviceSecuritySecret);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                paymenServicetUrl + "/internal/payments/process",
                request,
                String.class
        );
        return response.getBody();
    }
}
