package com.diana.auditinsightbackendspringboot.DTOs;


import com.diana.auditinsightbackendspringboot.Enum.Role;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class LoginMessage {
    private HttpStatus Status;
    private String Message;
    private String Token;
    @Enumerated(EnumType.STRING)
    private Role Role;
}
