package com.diana.auditinsightbackendspringboot.Authentication;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // inject the secret the same way Spring would via @Value
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-secret-key-that-is-long-enough-for-hs256-algorithm");
    }

    @Test
    void generateToken_returnsNonNullToken() {
        String token = jwtUtil.generateToken("user@test.com", "CLIENT");
        assertThat(token).isNotBlank();
    }

    @Test
    void extractUsername_returnsCorrectSubject() {
        String token = jwtUtil.generateToken("user@test.com", "CLIENT");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("user@test.com");
    }

    @Test
    void extractRole_returnsCorrectRole() {
        String token = jwtUtil.generateToken("user@test.com", "AUDITOR");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("AUDITOR");
    }

    @Test
    void isTokenExpired_freshToken_returnsFalse() {
        String token = jwtUtil.generateToken("user@test.com", "CLIENT");
        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }

    @Test
    void validateToken_matchingUsername_returnsTrue() {
        String token = jwtUtil.generateToken("user@test.com", "CLIENT");
        assertThat(jwtUtil.validateToken(token, "user@test.com")).isTrue();
    }

    @Test
    void validateToken_wrongUsername_returnsFalse() {
        String token = jwtUtil.generateToken("user@test.com", "CLIENT");
        assertThat(jwtUtil.validateToken(token, "other@test.com")).isFalse();
    }

    @Test
    void extractAllClaims_returnsAllExpectedClaims() {
        String token = jwtUtil.generateToken("claims@test.com", "ADMIN");
        Claims claims = jwtUtil.extractAllClaims(token);

        assertThat(claims.getSubject()).isEqualTo("claims@test.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.getExpiration()).isAfter(new java.util.Date());
    }

    @Test
    void extractAllClaims_invalidToken_throwsException() {
        assertThatThrownBy(() -> jwtUtil.extractAllClaims("not.a.valid.token"))
                .isInstanceOf(Exception.class);
    }
}
