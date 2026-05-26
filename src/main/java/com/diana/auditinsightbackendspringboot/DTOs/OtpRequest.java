package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OtpRequest {
    @Email(message = "Provide valid email", regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")
    private String email;

    private String otp;

}