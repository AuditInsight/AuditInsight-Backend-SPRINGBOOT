package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Authentication.JwtUtil;
import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.*;
import com.diana.auditinsightbackendspringboot.Repositories.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    public AuthService(UserRepository repo, PasswordEncoder encoder, JwtUtil jwtUtil,
                       ClientRepository clientRepository, AuditorRepository auditorRepository,
                       EmailService emailService, OtpRepository otpVerificationRepository) {
        this.repo = repo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
        this.clientRepository = clientRepository;
        this.auditorRepository = auditorRepository;
        this.emailService = emailService;
        this.otpVerificationRepository = otpVerificationRepository;
    }

    public Mono<ResponseMessage> registerUser(UserRegister request) {
        return repo.existsByUsername(request.getUsername())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.just(new ResponseMessage(HttpStatus.CONFLICT, "Email already registered"));
                    }

                    User user = new User();
                    user.setFullName(request.getFirstName().trim() + " " + request.getLastName().trim());
                    user.setUsername(request.getUsername());
                    user.setPassword(encoder.encode(request.getPassword()));
                    user.setRole(request.getRole());
                    user.setAuthProvider("JWT");

                    return repo.save(user).flatMap(savedUser -> {

                        if (savedUser.getRole() == Role.CLIENT) {
                            ClientProfile profile = new ClientProfile();
                            profile.setFirstName(request.getFirstName().trim());
                            profile.setLastName(request.getLastName().trim());
                            profile.setEmailAddress(request.getUsername());

                            return clientRepository.save(profile)
                                    .then(generateOtp(savedUser.getUsername()))
                                    .flatMap(otp ->
                                            Mono.fromRunnable(() -> emailService.sendVerificationEmail(
                                                            profile.getEmailAddress(), profile.getFirstName(), otp))
                                                    .subscribeOn(Schedulers.boundedElastic())
                                                    .thenReturn(new ResponseMessage(HttpStatus.CREATED,
                                                            "Successfully created an account. An OTP has been sent to your registered email."))
                                    );
                        } else if (savedUser.getRole() == Role.AUDITOR) {
                            AuditorProfile auditorProfile = new AuditorProfile();
                            auditorProfile.setFirstName(request.getFirstName());
                            auditorProfile.setLastName(request.getLastName());
                            auditorProfile.setEmailAddress(request.getUsername());

                            return auditorRepository.save(auditorProfile)
                                    .flatMap(savedProfile ->
                                            Mono.fromRunnable(() -> emailService.sendConfirmationEmail(
                                                            savedProfile.getEmailAddress(), savedProfile.getFirstName()))
                                                    .subscribeOn(Schedulers.boundedElastic())
                                                    .thenReturn(new ResponseMessage(HttpStatus.CREATED,
                                                            "Successfully created an account. Check your email address for confirmation."))
                                    );
                        }
                        return Mono.just(new ResponseMessage(HttpStatus.BAD_REQUEST, "Provided role is not supported"));
                    });
                });
    }

    public Mono<LoginMessage> login(LoginRequest request) {
        return repo.findByUsername(request.getUsername())
                .switchIfEmpty(Mono.error(new InvalidRecord("Username not found")))
                .flatMap(user -> {
                    if (!encoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.error(new InvalidRecord("Invalid credentials"));
                    }

                    if (user.getRole() == Role.CLIENT) {
                        return otpVerificationRepository.findByEmail(user.getUsername())
                                .switchIfEmpty(Mono.error(new InvalidRecord(
                                        "Your account is not active. Please verify your email using the OTP.")))
                                .flatMap(otp -> {
                                    if (!otp.isVerified()) {
                                        // BUG FIX: tell the user whether OTP has expired so they know to request a new one
                                        if (otp.getExpiry().isBefore(LocalDateTime.now())) {
                                            return Mono.error(new InvalidRecord(
                                                    "Your OTP has expired. Please request a new one."));
                                        }
                                        return Mono.error(new InvalidRecord(
                                                "Your account is not active. Please verify your email using the OTP."));
                                    }
                                    return generateTokenResponse(user);
                                });
                    } else if (user.getRole() == Role.AUDITOR) {
                        if (!user.isVerified()) {
                            return Mono.error(new InvalidRecord("Your account is waiting for admin approval."));
                        }
                        return generateTokenResponse(user);
                    }

                    return Mono.error(new InvalidRecord("Unknown user role"));
                });
    }

    private Mono<LoginMessage> generateTokenResponse(User user) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return Mono.just(new LoginMessage(HttpStatus.OK, "Successfully Login", token, user.getRole()));
    }

    // BUG FIX: delete any existing OTP for this email before saving a new one.
    // Without this, a user requesting a new OTP ends up with multiple OTP rows, and
    // findByEmail returns an unpredictable one — making resend/re-verify unreliable.
    private Mono<String> generateOtp(String email) {
        int otpCode = 100000 + new Random().nextInt(900000);
        OtpVerification otp = new OtpVerification();
        otp.setEmail(email);
        otp.setOtp(String.valueOf(otpCode));
        otp.setVerified(false);
        otp.setExpiry(LocalDateTime.now().plusMinutes(10));

        return otpVerificationRepository.deleteByEmail(email)
                .then(otpVerificationRepository.save(otp))
                .map(OtpVerification::getOtp);
    }

    public Mono<ResponseMessage> verifyOtp(OtpRequest request) {
        return otpVerificationRepository.findByEmailAndOtp(request.getEmail(), request.getOtp())
                .switchIfEmpty(Mono.error(new InvalidRecord("Invalid OTP or email.")))
                .flatMap(otp -> {
                    if (otp.isVerified()) {
                        return Mono.error(new InvalidRecord("Email already verified."));
                    }
                    if (otp.getExpiry().isBefore(LocalDateTime.now())) {
                        return Mono.error(new InvalidRecord("OTP expired. Please request a new one."));
                    }

                    otp.setVerified(true);
                    return otpVerificationRepository.save(otp)
                            .thenReturn(new ResponseMessage(HttpStatus.OK, "Successfully verified. Account Activated"));
                });
    }

    // BUG FIX: expose a resend OTP endpoint method so users who let their OTP expire aren't locked out
    public Mono<ResponseMessage> resendOtp(String email) {
        return repo.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("No account found for this email.")))
                .flatMap(user -> {
                    if (user.getRole() != Role.CLIENT) {
                        return Mono.error(new InvalidRecord("OTP verification is only required for client accounts."));
                    }
                    return otpVerificationRepository.findByEmail(email)
                            .flatMap(otp -> {
                                if (otp.isVerified()) {
                                    return Mono.just(new ResponseMessage(HttpStatus.OK, "Your account is already verified."));
                                }
                                return generateOtp(email)
                                        .flatMap(newOtp ->
                                                Mono.fromRunnable(() -> emailService.sendVerificationEmail(
                                                                email, user.getFullName(), newOtp))
                                                        .subscribeOn(Schedulers.boundedElastic())
                                                        .thenReturn(new ResponseMessage(HttpStatus.OK,
                                                                "A new OTP has been sent to your email."))
                                        );
                            })
                            .switchIfEmpty(
                                    generateOtp(email)
                                            .flatMap(newOtp ->
                                                    Mono.fromRunnable(() -> emailService.sendVerificationEmail(
                                                                    email, user.getFullName(), newOtp))
                                                            .subscribeOn(Schedulers.boundedElastic())
                                                            .thenReturn(new ResponseMessage(HttpStatus.OK,
                                                                    "A new OTP has been sent to your email."))
                                            )
                            );
                });
    }
}