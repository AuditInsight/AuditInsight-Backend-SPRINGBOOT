package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ResetPasswordRequest {

    @NotBlank
    private String email;

    @NotBlank
    private String otp;

    @NotBlank
    private String newPassword;
}
