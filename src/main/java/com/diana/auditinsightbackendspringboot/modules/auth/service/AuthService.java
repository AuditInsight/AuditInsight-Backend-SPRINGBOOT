package com.diana.auditinsightbackendspringboot.modules.auth.service;

import com.diana.auditinsightbackendspringboot.modules.auth.dto.*;
import com.diana.auditinsightbackendspringboot.modules.auth.entity.User;
import com.diana.auditinsightbackendspringboot.modules.auth.entity.repository.UserRepository;
import com.diana.auditinsightbackendspringboot.modules.auth.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final JwtUtil jwtUtil;  // add this

    public AuthService(UserRepository userRepository, PasswordEncoder PasswordEncoder, OtpService otpService, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = PasswordEncoder;
        this.otpService = otpService;
        this.jwtUtil = jwtUtil;
    }
 // SIGNUP
 public String signup(SignupRequest request) {

     if (userRepository.findByEmail(request.getEmail()).isPresent()) {
         throw new RuntimeException("Email already exists");
     }

     if (request.getPassword().length() < 6) {
         throw new RuntimeException("Password must be at least 6 characters");
     }

     User user = User.builder()
             .fullName(request.getFullName())
             .email(request.getEmail())
             .password(passwordEncoder.encode(request.getPassword()))
             .isVerified(false) // user must verify OTP first
             .build();

     User savedUser = userRepository.save(user);

     otpService.generateOTP(savedUser.getId()); // generate OTP after signup

     return "User registered successfully. Please verify your OTP.";
 }

    // LOGIN
    public String login(LoginRequest request) {
        // 1. Check if user exists
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Check password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Incorrect password");
        }

        // 3. Check OTP verification
        if (!user.getIsVerified()) {
            throw new RuntimeException("User not verified");
        }

        // 4. Generate JWT
        return jwtUtil.generateToken(user.getId(), user.getEmail());
    }
    // FORGOT PASSWORD
    public void forgotPassword(ForgotPasswordRequest request){
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        otpService.generateOTP(user.getId()); // send OTP for password reset
    }

    // RESET PASSWORD
    public void resetPassword(ResetPasswordRequest request){
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        boolean valid = otpService.verifyOtp(user.getId(), request.getOtp());
        if (!valid) throw new RuntimeException("Invalid OTP");
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    // VERIFY OTP (for signup)
    public void verifyOtp(Long userId, String code) {
        boolean valid = otpService.verifyOtp(userId, code);
        if (!valid) throw new RuntimeException("Invalid OTP");
        User user = userRepository.findById(userId).orElseThrow();
        user.setIsVerified(true);
        userRepository.save(user);
    }
}
