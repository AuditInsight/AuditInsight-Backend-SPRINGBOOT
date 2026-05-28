package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication and authorization endpoints for users of the system")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/sign-up")
    @Operation(summary = "Register new account")
    public Mono<ResponseEntity<ResponseMessage>> signup(@Valid @RequestBody UserRegister request) {
        return authService.registerUser(request)
                .map(response -> new ResponseEntity<>(response, response.getStatus()));
    }

    @PostMapping("/login")
    @Operation(summary = "Login user")
    public Mono<ResponseEntity<LoginMessage>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request)
                .map(response -> new ResponseEntity<>(response, HttpStatus.OK));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP")
    public Mono<ResponseEntity<ResponseMessage>> verifyOtp(@Valid @RequestBody OtpRequest otpRequest) {
        return authService.verifyOtp(otpRequest)
                .map(response -> new ResponseEntity<>(response, HttpStatus.OK));
    }


    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP to email")
    public Mono<ResponseEntity<ResponseMessage>> resendOtp(@RequestParam String email) {
        return authService.resendOtp(email)
                .map(response -> new ResponseEntity<>(response, HttpStatus.OK));
    }
    @GetMapping("/social-login/{provider}")
    @Operation(
            summary = "Social login redirect",
            description = "Redirects the user to the OAuth2 authentication provider"
    )
    public Mono<Void> redirectToProvider(
            @PathVariable String provider,
            ServerHttpResponse response) {

        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create("/oauth2/authorization/" + provider));
        return response.setComplete();
    }
}