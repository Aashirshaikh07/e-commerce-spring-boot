package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.userdto.LoginRequest;
import com.aashir.ecommerce.dto.userdto.LoginResponse;
import com.aashir.ecommerce.dto.userdto.RegisterRequest;
import com.aashir.ecommerce.entity.User;
import com.aashir.ecommerce.entity.UserStatus;
import com.aashir.ecommerce.exception.DuplicateResourceException;
import com.aashir.ecommerce.repository.UserRepository;
import com.aashir.ecommerce.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationEventFactory notificationEventFactory;


    @Transactional
    public User registerUser(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("This email is already registered");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("This phone number is already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setStatus(UserStatus.ACTIVE);
        user.setRole("USER");

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("Email or phone number is already registered");
        }

        eventPublisher.publishEvent(
                notificationEventFactory.userRegistered(savedUser.getEmail(), savedUser.getName()));
        return savedUser;
    }

    public LoginResponse login(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException(loginRequest.getEmail()));

        String jwt = jwtService.generateToken(user.getEmail(),user.getId(),user.getRole());

        return new LoginResponse(jwt,"Bearer");
    }
}