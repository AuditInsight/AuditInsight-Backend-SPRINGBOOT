package com.diana.auditinsightbackendspringboot.modules.auth.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class SignupRequest {
    @NotBlank
    private String fullName;

    @Email
    private String email;
}
