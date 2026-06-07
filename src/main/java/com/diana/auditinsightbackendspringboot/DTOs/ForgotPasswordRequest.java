package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.Data;
import jakarta.validation.constraints.Email;

@Data
public class ForgotPasswordRequest {
    @Email
    private String email;
}
