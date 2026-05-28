package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.Role;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRegister {

    @NotNull(message = "Provide the first name")
    private String firstName;

    @NotBlank(message = "last name can not be blank")
    private String lastName;

    @Email(message = "Provide valid email", regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")
    @NotBlank(message = "the email is missing")
    private String username;

    @Pattern(message = "the password should be valid", regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")
    @NotBlank(message = "this field can not be empty")
    private String password;

    @NotNull(message = "you must provide the role(CLIENT / AUDITOR)")
    private Role role;
}
