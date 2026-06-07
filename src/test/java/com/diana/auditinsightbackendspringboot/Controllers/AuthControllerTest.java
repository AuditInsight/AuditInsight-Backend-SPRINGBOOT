package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.LoginMessage;
import com.diana.auditinsightbackendspringboot.DTOs.ResponseMessage;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Exceptions.Global.GlobalExceptionHandler;
import com.diana.auditinsightbackendspringboot.Services.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient
                .bindToController(new AuthController(authService))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ──────────────────────────── /sign-up ────────────────────────────

    @Test
    void signup_validClientRequest_returns201() {
        when(authService.registerUser(any())).thenReturn(
                Mono.just(new ResponseMessage(HttpStatus.CREATED,
                        "Successfully created an account. An OTP has been sent to your registered email.")));

        webTestClient.post().uri("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "firstName": "Alice",
                          "lastName": "Smith",
                          "username": "alice@example.com",
                          "password": "Password1@",
                          "role": "CLIENT"
                        }""")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.message").isEqualTo(
                        "Successfully created an account. An OTP has been sent to your registered email.");
    }

    @Test
    void signup_duplicateEmail_returns409() {
        when(authService.registerUser(any())).thenReturn(
                Mono.just(new ResponseMessage(HttpStatus.CONFLICT, "Email already registered")));

        webTestClient.post().uri("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "firstName": "Alice",
                          "lastName": "Smith",
                          "username": "alice@example.com",
                          "password": "Password1@",
                          "role": "CLIENT"
                        }""")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Email already registered");
    }

    @Test
    void signup_validAuditorRequest_returns201() {
        when(authService.registerUser(any())).thenReturn(
                Mono.just(new ResponseMessage(HttpStatus.CREATED,
                        "Successfully created an account. Check your email address for confirmation.")));

        webTestClient.post().uri("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "firstName": "Bob",
                          "lastName": "Jones",
                          "username": "bob@example.com",
                          "password": "Password1@",
                          "role": "AUDITOR"
                        }""")
                .exchange()
                .expectStatus().isCreated();
    }

    // ──────────────────────────── /login ────────────────────────────

    @Test
    void login_validCredentials_returns200WithToken() {
        when(authService.login(any())).thenReturn(
                Mono.just(new LoginMessage(HttpStatus.OK, "Successfully Login", "jwt.token.here", Role.CLIENT, false)));

        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "alice@example.com",
                          "password": "Password1@"
                        }""")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("jwt.token.here")
                .jsonPath("$.role").isEqualTo("CLIENT");
    }

    @Test
    void login_invalidCredentials_returns400() {
        when(authService.login(any())).thenReturn(
                Mono.error(new InvalidRecord("Invalid credentials")));

        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "alice@example.com",
                          "password": "Password1@"
                        }""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Invalid credentials");
    }

    @Test
    void login_unverifiedAccount_returns400() {
        when(authService.login(any())).thenReturn(
                Mono.error(new InvalidRecord("Your account is not active. Please verify your email using the OTP.")));

        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "alice@example.com",
                          "password": "Password1@"
                        }""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Your account is not active. Please verify your email using the OTP.");
    }

    @Test
    void login_socialLoginUser_returns400() {
        when(authService.login(any())).thenReturn(
                Mono.error(new InvalidRecord("This account uses social login. Please sign in with google.")));

        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "alice@example.com",
                          "password": "Password1@"
                        }""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("This account uses social login. Please sign in with google.");
    }

    // ──────────────────────────── /verify-otp ────────────────────────────

    @Test
    void verifyOtp_validOtp_returns200() {
        when(authService.verifyOtp(any())).thenReturn(
                Mono.just(new ResponseMessage(HttpStatus.OK, "Successfully verified. Account Activated")));

        webTestClient.post().uri("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "email": "alice@example.com",
                          "otp": "123456"
                        }""")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Successfully verified. Account Activated");
    }

    @Test
    void verifyOtp_invalidOtp_returns400() {
        when(authService.verifyOtp(any())).thenReturn(
                Mono.error(new InvalidRecord("Invalid OTP or email.")));

        webTestClient.post().uri("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "email": "alice@example.com",
                          "otp": "000000"
                        }""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Invalid OTP or email.");
    }

    @Test
    void verifyOtp_alreadyVerified_returns400() {
        when(authService.verifyOtp(any())).thenReturn(
                Mono.error(new InvalidRecord("Email already verified.")));

        webTestClient.post().uri("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "email": "alice@example.com",
                          "otp": "123456"
                        }""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Email already verified.");
    }

    // ──────────────────────────── /resend-otp ────────────────────────────

    @Test
    void resendOtp_knownEmail_returns200() {
        when(authService.resendOtp(anyString())).thenReturn(
                Mono.just(new ResponseMessage(HttpStatus.OK, "A new OTP has been sent to your email.")));

        webTestClient.post().uri("/api/auth/resend-otp?email=alice@example.com")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("A new OTP has been sent to your email.");
    }

    @Test
    void resendOtp_unknownEmail_returns400() {
        when(authService.resendOtp(anyString())).thenReturn(
                Mono.error(new InvalidRecord("No account found for this email.")));

        webTestClient.post().uri("/api/auth/resend-otp?email=nobody@example.com")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("No account found for this email.");
    }
}
