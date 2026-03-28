package com.diana.auditinsightbackendspringboot.modules.auth.security;

import io.jsonwebtoken.*;
import org.springframework.*;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    private final String SECRET_KEY = "mySecretKey"; // keep this a secret
    private final long EXPIRATION_MS = 1000 * 60 * 60;  // 1 hour

    // Generate token
    public String generateToken(Long userId, String email){

    }

}
