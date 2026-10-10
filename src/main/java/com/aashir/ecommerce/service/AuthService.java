package com.aashir.ecommerce.service;

import com.aashir.ecommerce.dto.userdto.LoginRequest;
import com.aashir.ecommerce.dto.userdto.LoginResponse;
import com.aashir.ecommerce.dto.userdto.RegisterRequest;
import com.aashir.ecommerce.entity.User;
import com.aashir.ecommerce.entity.UserStatus;
import com.aashir.ecommerce.event.UserRegisteredEvent;
import com.aashir.ecommerce.repository.UserRepository;
import com.aashir.ecommerce.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;


    public User registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }
//        if (userRepository.existsByUsername(request.getName())) {
//            throw new RuntimeException("Username already in use");
//        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setPhone(request.getPhone());
        user.setStatus(UserStatus.ACTIVE);
        user.setRole("USER");

        User savedUser = userRepository.save(user);

        UserRegisteredEvent event = new UserRegisteredEvent(savedUser.getName(), savedUser.getEmail());
        if(TransactionSynchronizationManager.isActualTransactionActive()){
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(event);
                }
            });
        }else {
            eventPublisher.publishEvent(event);
        }
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