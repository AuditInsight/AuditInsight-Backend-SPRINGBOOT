package com.diana.auditinsightbackendspringboot.DTOs;


import com.diana.auditinsightbackendspringboot.Enum.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class LoginMessage {
    private HttpStatus status;
    private String message;
    private String token;
    private Role role;
    private boolean mustChangePassword;
}
