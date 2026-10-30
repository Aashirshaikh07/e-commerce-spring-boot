package com.aashir.ecommerce.service;

import com.aashir.ecommerce.config.PasswordResetProperties;
import com.aashir.ecommerce.entity.PasswordResetOtp;
import com.aashir.ecommerce.entity.PasswordResetStatus;
import com.aashir.ecommerce.entity.User;
import com.aashir.ecommerce.entity.UserStatus;
import com.aashir.ecommerce.event.NotificationEvent;
import com.aashir.ecommerce.event.NotificationType;
import com.aashir.ecommerce.exception.InvalidPasswordResetException;
import com.aashir.ecommerce.repository.PasswordResetOtpRepository;
import com.aashir.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetOtpRepository otpRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OtpGenerator otpGenerator;
    @Mock
    private ResetTokenGenerator resetTokenGenerator;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private NotificationEventFactory notificationEventFactory;

    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                userRepository,
                otpRepository,
                passwordEncoder,
                otpGenerator,
                resetTokenGenerator,
                new PasswordResetProperties(),
                eventPublisher,
                notificationEventFactory);

        user = new User();
        user.setEmail("a@b.com");
        user.setName("Aashir");
        user.setStatus(UserStatus.ACTIVE);
    }

    private PasswordResetOtp activeOtp() {
        PasswordResetOtp record = new PasswordResetOtp();
        record.setUser(user);
        record.setOtpHash("hash");
        record.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        record.setAttempts(0);
        record.setStatus(PasswordResetStatus.ACTIVE);
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    @Test
    void unknownEmailDoesNothing() {
        when(userRepository.findByEmail("x@y.com")).thenReturn(Optional.empty());

        service.requestReset("x@y.com");

        verify(otpRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void requestWithinCooldownIsIgnored() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(otpRepository.findFirstByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.of(activeOtp()));

        service.requestReset("a@b.com");

        verify(otpRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void requestStoresHashedOtpAndPublishesEvent() {
        NotificationEvent event = new NotificationEvent(
                "password-reset-7", NotificationType.PASSWORD_RESET_OTP,
                Instant.now(), "a@b.com", "Aashir", Map.of());

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(otpRepository.findFirstByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.empty());
        when(otpGenerator.generate(6)).thenReturn("123456");
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(otpRepository.save(any(PasswordResetOtp.class))).thenAnswer(invocation -> {
            PasswordResetOtp saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        when(notificationEventFactory.passwordResetOtp("a@b.com", "Aashir", "123456", 10, 7L)).thenReturn(event);

        service.requestReset("a@b.com");

        ArgumentCaptor<PasswordResetOtp> captor = ArgumentCaptor.forClass(PasswordResetOtp.class);
        verify(otpRepository).save(captor.capture());
        assertEquals("encoded", captor.getValue().getOtpHash());
        assertEquals(PasswordResetStatus.ACTIVE, captor.getValue().getStatus());
        verify(otpRepository).updateStatusForUser(eq(user), any(), eq(PasswordResetStatus.INVALIDATED));
        verify(eventPublisher).publishEvent(event);
    }

    @Test
    void correctOtpReturnsResetToken() {
        PasswordResetOtp record = activeOtp();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(otpRepository.findFirstByUserAndStatusOrderByCreatedAtDesc(user, PasswordResetStatus.ACTIVE))
                .thenReturn(Optional.of(record));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
        when(resetTokenGenerator.generate()).thenReturn("token");
        when(resetTokenGenerator.hash("token")).thenReturn("tokenHash");

        String token = service.verifyOtp("a@b.com", "123456");

        assertEquals("token", token);
        assertEquals(PasswordResetStatus.VERIFIED, record.getStatus());
        assertEquals("tokenHash", record.getResetTokenHash());
    }

    @Test
    void wrongOtpIncrementsAttempts() {
        PasswordResetOtp record = activeOtp();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(otpRepository.findFirstByUserAndStatusOrderByCreatedAtDesc(user, PasswordResetStatus.ACTIVE))
                .thenReturn(Optional.of(record));
        when(passwordEncoder.matches("000000", "hash")).thenReturn(false);

        assertThrows(InvalidPasswordResetException.class, () -> service.verifyOtp("a@b.com", "000000"));

        assertEquals(1, record.getAttempts());
        assertEquals(PasswordResetStatus.ACTIVE, record.getStatus());
    }

    @Test
    void fifthWrongAttemptInvalidatesOtp() {
        PasswordResetOtp record = activeOtp();
        record.setAttempts(4);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(otpRepository.findFirstByUserAndStatusOrderByCreatedAtDesc(user, PasswordResetStatus.ACTIVE))
                .thenReturn(Optional.of(record));
        when(passwordEncoder.matches("000000", "hash")).thenReturn(false);

        assertThrows(InvalidPasswordResetException.class, () -> service.verifyOtp("a@b.com", "000000"));

        assertEquals(5, record.getAttempts());
        assertEquals(PasswordResetStatus.INVALIDATED, record.getStatus());
    }

    @Test
    void expiredOtpFailsWithoutCheckingTheCode() {
        PasswordResetOtp record = activeOtp();
        record.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(otpRepository.findFirstByUserAndStatusOrderByCreatedAtDesc(user, PasswordResetStatus.ACTIVE))
                .thenReturn(Optional.of(record));

        assertThrows(InvalidPasswordResetException.class, () -> service.verifyOtp("a@b.com", "123456"));

        assertEquals(PasswordResetStatus.INVALIDATED, record.getStatus());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void validTokenResetsPassword() {
        PasswordResetOtp record = activeOtp();
        record.setStatus(PasswordResetStatus.VERIFIED);
        record.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(resetTokenGenerator.hash("token")).thenReturn("h");
        when(otpRepository.findByResetTokenHash("h")).thenReturn(Optional.of(record));
        when(passwordEncoder.encode("newPass123")).thenReturn("newEncoded");

        service.resetPassword("token", "newPass123");

        assertEquals("newEncoded", user.getPassword());
        assertEquals(PasswordResetStatus.USED, record.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void usedTokenIsRejected() {
        PasswordResetOtp record = activeOtp();
        record.setStatus(PasswordResetStatus.USED);
        record.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(resetTokenGenerator.hash("token")).thenReturn("h");
        when(otpRepository.findByResetTokenHash("h")).thenReturn(Optional.of(record));

        assertThrows(InvalidPasswordResetException.class, () -> service.resetPassword("token", "newPass123"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void expiredTokenIsRejected() {
        PasswordResetOtp record = activeOtp();
        record.setStatus(PasswordResetStatus.VERIFIED);
        record.setResetTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(resetTokenGenerator.hash("token")).thenReturn("h");
        when(otpRepository.findByResetTokenHash("h")).thenReturn(Optional.of(record));

        assertThrows(InvalidPasswordResetException.class, () -> service.resetPassword("token", "newPass123"));

        verify(userRepository, never()).save(any());
    }
}