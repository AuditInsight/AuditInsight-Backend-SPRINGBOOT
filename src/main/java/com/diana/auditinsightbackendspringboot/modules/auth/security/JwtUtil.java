package com.diana.auditinsightbackendspringboot.modules.auth.security;

import io.jsonwebtoken.*;
import org.springframework.*;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    private final String SECRET_KEY = "mySecretKey"; // keep this a secret
}
