package com.aashir.ecommerce.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column( nullable = false)
    @Size(max = 50)
    private String name;

    @Column(nullable = false,unique = true)
    @Size(max = 50,message = "Character cannot be greater than 50")
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false,unique = true)
    @Size(max = 50)
    private String phone;

    @OneToMany(mappedBy = "user",cascade = CascadeType.ALL,orphanRemoval = true)
    List<Address> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    List<Order> orders = new ArrayList<>();

    @Column(nullable = false)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    protected LocalDateTime createdAt;
    protected LocalDateTime updatedAt;
    protected LocalDateTime deletedAt;

    @PrePersist
    public void onCreate()
    {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate()
    {
        this.updatedAt = LocalDateTime.now();
    }

    @PreRemove
    public void onDelete()
    {
        this.deletedAt = LocalDateTime.now();
    }



}
