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
    @Column( nullable = false)
    private ADDRESS_TYPE address_type;

    @Column( nullable = false)
    private String area;

    @Column( nullable = false)
    private String city;

    @Column( nullable = false)
    private String state;

    @Column( nullable = false)
    private Integer pincode;

    private LocalDateTime last_update;

    @PrePersist
    protected void onUpdate() {
        last_update = LocalDateTime.now();
    }

}
