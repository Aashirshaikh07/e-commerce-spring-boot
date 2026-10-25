package com.aashir.ecommerce.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "address")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false)
    private ADDRESS_TYPE addressType;

    @Column( nullable = false)
    private String area;

    @Column( nullable = false)
    private String city;

    @Column( nullable = false)
    private String state;

    @Column( nullable = false,length = 6)
    private String pincode;

    @Column(name = "last_update")
    private LocalDateTime lastUpdate;

    @PrePersist
    @PreUpdate
    protected void touch() {
        lastUpdate = LocalDateTime.now();
    }
}
