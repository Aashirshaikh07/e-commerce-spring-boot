package com.aashir.ecommerce.service;

import com.aashir.ecommerce.config.PasswordResetProperties;
import com.aashir.ecommerce.entity.PasswordResetOtp;
import com.aashir.ecommerce.entity.PasswordResetStatus;
import com.aashir.ecommerce.entity.User;
import com.aashir.ecommerce.entity.UserStatus;
import com.aashir.ecommerce.exception.InvalidPasswordResetException;
import com.aashir.ecommerce.repository.PasswordResetOtpRepository;
import com.aashir.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpGenerator otpGenerator;
    private final ResetTokenGenerator resetTokenGenerator;
    private final PasswordResetProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationEventFactory notificationEventFactory;

    @Transactional
    public void requestReset(String email) {
        Optional<User> foundUser = userRepository.findByEmail(email);
        if (foundUser.isEmpty()) {
            return;
        }

        User user = foundUser.get();
        if(user.getStatus() != UserStatus.ACTIVE){
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<PasswordResetOtp> latest = otpRepository.findFirstByUserOrderByCreatedAtDesc(user);
        if(latest.isPresent() && latest.get().getCreatedAt().plusSeconds(properties.getResendCooldownSeconds()).isAfter(now)){
            return;
        }

        otpRepository.updateStatusForUser(
                user,
                EnumSet.of(PasswordResetStatus.ACTIVE, PasswordResetStatus.VERIFIED),
                PasswordResetStatus.INVALIDATED);

        String otp = otpGenerator.generate(properties.getOtpLength());

        PasswordResetOtp record = new PasswordResetOtp();
        record.setUser(user);
        record.setOtpHash(passwordEncoder.encode(otp));
        record.setExpiresAt(now.plusMinutes(properties.getOtpExpiryMinutes()));
        record.setAttempts(0);
        record.setStatus(PasswordResetStatus.ACTIVE);
        PasswordResetOtp saved = otpRepository.save(record);

        eventPublisher.publishEvent(notificationEventFactory.passwordResetOtp(
                user.getEmail(),
                user.getName(),
                otp,
                properties.getOtpExpiryMinutes(),
                saved.getId()));
    }

    @Transactional(noRollbackFor = InvalidPasswordResetException.class)
    public String verifyOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidPasswordResetException::new);

        PasswordResetOtp record = otpRepository
                .findFirstByUserAndStatusOrderByCreatedAtDesc(user, PasswordResetStatus.ACTIVE)
                .orElseThrow(InvalidPasswordResetException::new);

        LocalDateTime now = LocalDateTime.now();

        if (record.getExpiresAt().isBefore(now)
                || record.getAttempts() >= properties.getMaxAttempts()) {
            record.setStatus(PasswordResetStatus.INVALIDATED);
            otpRepository.save(record);
            throw new InvalidPasswordResetException();
        }

        if (!passwordEncoder.matches(otp, record.getOtpHash())) {
            record.setAttempts(record.getAttempts() + 1);
            if (record.getAttempts() >= properties.getMaxAttempts()) {
                record.setStatus(PasswordResetStatus.INVALIDATED);
            }
            otpRepository.save(record);
            throw new InvalidPasswordResetException();
        }

        String token = resetTokenGenerator.generate();
        record.setStatus(PasswordResetStatus.VERIFIED);
        record.setResetTokenHash(resetTokenGenerator.hash(token));
        record.setResetTokenExpiresAt(now.plusMinutes(properties.getTokenExpiryMinutes()));
        otpRepository.save(record);

        return token;
    }

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        PasswordResetOtp record = otpRepository
                .findByResetTokenHash(resetTokenGenerator.hash(resetToken))
                .orElseThrow(InvalidPasswordResetException::new);

        if (record.getStatus() != PasswordResetStatus.VERIFIED
                || record.getResetTokenExpiresAt() == null
                || record.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidPasswordResetException();
        }

        User user = record.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        record.setStatus(PasswordResetStatus.USED);
        otpRepository.save(record);
    }

    }
