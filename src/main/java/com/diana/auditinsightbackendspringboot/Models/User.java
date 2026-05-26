package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.validator.constraints.Length;

import java.sql.Timestamp;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(unique = true, nullable = false)
    private String username;


    private String password;

    @Column(nullable = false)
    private String fullName;


    @Enumerated(EnumType.STRING)
    @NotNull(message = "the role must always be provided")
    private Role Role;

    @NotBlank
    private String AuthProvider;

    private boolean verified = false;

    @CreationTimestamp
    private Timestamp Created_At;

}