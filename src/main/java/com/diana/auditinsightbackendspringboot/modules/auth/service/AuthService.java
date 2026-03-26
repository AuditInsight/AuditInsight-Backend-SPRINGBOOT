package com.diana.auditinsightbackendspringboot.modules.auth.service;

import com.diana.auditinsightbackendspringboot.modules.auth.dto.*;
import com.diana.auditinsightbackendspringboot.modules.auth.entity.User;
import com.diana.auditinsightbackendspringboot.modules.auth.otp.OtpEntity;
import com.diana.auditinsightbackendspringboot.modules.auth.entity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    public AuthService(UserRepository userRepository,PasswordEncoder passwordEncoder,OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
    }
 // SIGNUP
    public User signup(SignupRequest request) {
        if(userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exist")
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .isVerified(false)
                .build();

        user = userRepository.save(user);
        otpService.generateOTP(user.getId()); // send OTP
        return user;
    }

    // LOGIN
    public User login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())){
            throw new RuntimeException("Incorrect password");
        }
        if (!user.getIsVerified()){
            throw new RuntimeException("User not verified");
        }
        return user;
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
}
