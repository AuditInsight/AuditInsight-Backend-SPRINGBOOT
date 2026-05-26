package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Authentication.JwtUtil;
import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.AuditorProfile;
import com.diana.auditinsightbackendspringboot.Models.ClientProfile;
import com.diana.auditinsightbackendspringboot.Models.OtpVerification;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.AuditorRepository;
import com.diana.auditinsightbackendspringboot.Repositories.ClientRepository;
import com.diana.auditinsightbackendspringboot.Repositories.OtpRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class AuthService {

    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final ClientRepository clientRepository;
    private final AuditorRepository auditorRepository;
    private final EmailService emailService;
    private final OtpRepository otpVerificationRepository;

    public AuthService(UserRepository repo, PasswordEncoder encoder, JwtUtil jwtUtil ,
                       ClientRepository clientRepository , AuditorRepository auditorRepository ,
                       EmailService emailService, OtpRepository otpVerification) {
        this.repo = repo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
        this.clientRepository = clientRepository;
        this.auditorRepository = auditorRepository;
        this.emailService = emailService;
        this.otpVerificationRepository = otpVerification;

    }

    public ResponseMessage registerUser(UserRegister userRegister) {
        if (repo.existsByUsername(userRegister.getUsername())){
            return new ResponseMessage(HttpStatus.CONFLICT , "Email already registered");

        }
        User user = new User();
        user.setFullName(userRegister.getFirstName().trim() + " " + userRegister.getLastName().trim());
        user.setUsername(userRegister.getUsername());
        user.setPassword(encoder.encode(userRegister.getPassword()));
        user.setRole(userRegister.getRole());
        user.setAuthProvider("JWT");
        repo.save(user);

        if (userRegister.getRole() == Role.CLIENT) {
            ClientProfile profile = new ClientProfile();
            profile.setFirstName(userRegister.getFirstName().trim());
            profile.setLastName(userRegister.getLastName().trim());
            profile.setEmailAddress(userRegister.getUsername());
            clientRepository.save(profile);

            String OTP = generateOtp(userRegister.getUsername());



            emailService.sendVerificationEmail(profile.getEmailAddress() , profile.getFirstName(), OTP);

            return new ResponseMessage(HttpStatus.CREATED , "Successfully created an account. An OTP has been sent to your registered email address for account verification.");

        } else if (userRegister.getRole() == Role.AUDITOR) {
            AuditorProfile auditorProfile = new AuditorProfile();
            auditorProfile.setFirstName(userRegister.getFirstName());
            auditorProfile.setLastName(userRegister.getLastName());
            auditorProfile.setEmailAddress(userRegister.getUsername());
            auditorRepository.save(auditorProfile);


            emailService.sendConfirmationEmail(auditorProfile.getEmailAddress(), auditorProfile.getFirstName());

            return new ResponseMessage(HttpStatus.CREATED ,"Successfully created an account. Check your email address for confirmation");

        }
        else {
            return new ResponseMessage(HttpStatus.BAD_REQUEST , "Provided role is not supported");
        }


    }



    public LoginMessage login(LoginRequest request) throws InvalidRecord {

        User user = repo.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidRecord("Username not found"));

        if (!encoder.matches(request.getPassword(), user.getPassword()))
            throw new InvalidRecord("Invalid credentials");


        if (user.getRole().equals(Role.CLIENT)) {
            OtpVerification otp = otpVerificationRepository.findByEmail(user.getUsername());

            if (otp == null || !otp.isVerified()) {
                throw new InvalidRecord("Your account is not active. Please verify your email using the OTP.");
            }
            user.setVerified(true);

        }
        else if (user.getRole().equals(Role.AUDITOR)) {

            AuditorProfile auditor = auditorRepository.findByEmailAddress(user.getUsername())
                    .orElseThrow(() -> new InvalidRecord("Auditor profile not found."));

            if (!user.isVerified()) {
                throw new InvalidRecord("Your account is waiting for  admin approval.");
            }
        }

        return new LoginMessage(HttpStatus.OK ,"Successfully Login" , jwtUtil.generateToken(user.getUsername(), user.getRole().name()), user.getRole());
    }


    private  String generateOtp(String email) {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        OtpVerification otpVerification = new OtpVerification();
        otpVerification.setEmail(email);
        otpVerification.setOtp(String.valueOf(otp));
        otpVerification.setVerified(false);
        otpVerification.setExpiry(LocalDateTime.now().plusMinutes(10));
        otpVerificationRepository.save(otpVerification);
        return String.valueOf(otp);
    }
    public ResponseMessage verifyOtp(OtpRequest request) {
        OtpVerification otp = otpVerificationRepository.findByEmailAndOtp(request.getEmail(), request.getOtp()).orElseThrow(() -> new IllegalStateException("Invalid OTP or email."));
        if (otp.isVerified()) {
            throw new IllegalStateException("Email already verified.");
        }
        if (otp.getExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("OTP expired. Please request a new one.");
        }
        otp.setVerified(true);
        otpVerificationRepository.save(otp);
        return new ResponseMessage(HttpStatus.OK,"Successfully verified. Account Activated");
    }
}
