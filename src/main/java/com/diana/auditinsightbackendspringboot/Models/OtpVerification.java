package com.diana.auditinsightbackendspringboot.Models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Data
@Table("otp_verification")
public class OtpVerification {
    @Id
    private Long id;
    private String email;
    private String otp;
    private boolean verified;
    private LocalDateTime expiry;
}