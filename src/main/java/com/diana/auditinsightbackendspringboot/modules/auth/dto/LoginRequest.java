package com.diana.auditinsightbackendspringboot.modules.auth.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Data
public class LoginRequest {
    @Email
    private String email;

    @NotBlank
    private String password;
}
