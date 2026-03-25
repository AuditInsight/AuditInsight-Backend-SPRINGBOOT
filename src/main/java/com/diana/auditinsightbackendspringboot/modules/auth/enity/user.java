package com.diana.auditinsightbackendspringboot.modules.auth.enity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class user {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password; // stores hashed passwords only.

    @Column(nullable = false)
    private Boolean isVerified = false;  // to track OTP verification.
}
