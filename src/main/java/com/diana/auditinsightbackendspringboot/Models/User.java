package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column("id")
    private Long id;

    @Column("username")
    private String username;

    @Column("password")
    private String password;

    @Column("full_name")
    private String fullName;

    @Column("role")
    private Role role;

    @Column("auth_provider")
    private String authProvider;

    @Column("verified")
    private boolean verified = false;

    @Column("active")
    private boolean active = true;

    @Column("must_change_password")
    private boolean mustChangePassword = false;

    @Column("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}