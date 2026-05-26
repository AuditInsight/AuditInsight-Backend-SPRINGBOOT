package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Services.AuthService;
//import com.diana.auditinsightbackendspringboot.modules.auth.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/auth")
@Tag(name="Authentication" , description = "Authentication and authorization endpoints for users of the system")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/sign-up")
    @Operation(
            summary = "Register new account",
            description = "Creates a new  account to a new user by providing all the required information and providing the role (CLIENT /AUDITOR)"
    )
    public ResponseEntity<ResponseMessage> signup(@Valid @RequestBody UserRegister request) {
     return new ResponseEntity<>(authService.registerUser(request), HttpStatus.CREATED);
    }
    @PostMapping("/login")
    @Operation(
            summary = "Login user",
            description = "Authenticates a user and returns JWT authentication details"
    )
    public ResponseEntity<LoginMessage> login(@Valid @RequestBody LoginRequest request) throws InvalidRecord {
      return new ResponseEntity<>(authService.login(request) , HttpStatus.OK);
    }

//    @PostMapping("/forgot-password")
//    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
//        authService.forgotPassword(request);
//        return ResponseEntity.ok("OTP sent to your email.");
//    }
//
//    @PostMapping("/reset-password")
//    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
//        authService.resetPassword(request);
//        return ResponseEntity.ok("Password reset successful.");
//    }

    @PostMapping("/verify-otp")
    @Operation(
            summary = "Verify OTP",
            description = "Verifies the one-time password sent to the user email"
    )
    public ResponseEntity<ResponseMessage> verifyOtp(@Valid @RequestBody OtpRequest otpRequest){
        return new ResponseEntity<>(authService.verifyOtp(otpRequest), HttpStatus.OK);
    }


    @GetMapping("/social-login/{provider}")
    @Operation(
            summary = "Social login redirect",
            description = "Redirects the user to the OAuth2 authentication provider"
    )
    public void redirectToProvider(@PathVariable String provider, HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/" + provider);
    }

}