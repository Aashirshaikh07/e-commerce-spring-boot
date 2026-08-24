package com.aashir.ecommerce.repository;

import com.aashir.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
    SELECT DISTINCT u
    FROM User u
    LEFT JOIN FETCH u.orders
    WHERE u.email = :email
""")
    Optional<User> findUserWithOrdersByEmail(@Param("email") String email);
}
