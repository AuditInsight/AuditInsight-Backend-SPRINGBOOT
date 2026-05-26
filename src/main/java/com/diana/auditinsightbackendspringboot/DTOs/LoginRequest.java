package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @Email(message = "the email must be valid", regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$ ")
    @NotBlank(message = "the email is missing")
    private String username;

    @Pattern(message = "the  password should be valid ", regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")
    @NotBlank(message = "this field can not be empty")
    private String password;
}
