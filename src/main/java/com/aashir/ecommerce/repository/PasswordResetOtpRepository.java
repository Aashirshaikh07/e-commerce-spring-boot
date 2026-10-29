package com.aashir.ecommerce.repository;

import com.aashir.ecommerce.entity.PasswordResetOtp;
import com.aashir.ecommerce.entity.PasswordResetStatus;
import com.aashir.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
    Optional<PasswordResetOtp> findFirstByUserOrderByCreatedAtDesc(User user);

    Optional<PasswordResetOtp> findFirstByUserAndStatusOrderByCreatedAtDesc(User user, PasswordResetStatus status);

    Optional<PasswordResetOtp> findByResetTokenHash(String resetTokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetOtp o set o.status = :newStatus where o.user = :user and o.status in :statuses")
    int updateStatusForUser(@Param("user") User user,
                            @Param("statuses") Collection<PasswordResetStatus> statuses,
                            @Param("newStatus") PasswordResetStatus newStatus);
}
