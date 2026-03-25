package com.diana.auditinsightbackendspringboot.modules.auth.service;

import com.diana.auditinsightbackendspringboot.modules.auth.otp.OtpRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Random;

@Service
public class OtpService {

    private final OtpRepository otpRepository;

    public OtpService(OtpRepository otpRepository) {
        this.otpRepository = otpRepository;
    }
}
