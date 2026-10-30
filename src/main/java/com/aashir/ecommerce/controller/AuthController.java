package com.aashir.ecommerce.controller;

import com.aashir.ecommerce.dto.userdto.LoginRequest;
import com.aashir.ecommerce.dto.userdto.LoginResponse;
import com.aashir.ecommerce.dto.userdto.RegisterRequest;
import com.aashir.ecommerce.dto.userdto.RegisterResponse;
import com.aashir.ecommerce.entity.User;
import com.aashir.ecommerce.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        User savedUser = authService.registerUser(registerRequest);
        RegisterResponse response = new RegisterResponse(
                savedUser.getId(), savedUser.getName(), savedUser.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse loginResponse = authService.login(loginRequest);
        return ResponseEntity.ok(loginResponse);
    }
}
