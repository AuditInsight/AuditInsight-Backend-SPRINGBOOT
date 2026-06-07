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
    private Long id;

    @Column("username")
    private String username;

    private String password;

    @Column("full_name")
    private String fullName;

    private Role role;

    @Column("auth_provider")
    private String authProvider;

    private boolean verified = false;

    @Column("must_change_password")
    private boolean mustChangePassword = false;

    @Column("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}