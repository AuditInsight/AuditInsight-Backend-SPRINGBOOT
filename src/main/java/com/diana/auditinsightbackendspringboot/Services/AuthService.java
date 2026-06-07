package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Authentication.JwtUtil;
import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Enum.InvitationStatus;
import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
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
    private final OrganisationInvitationRepository invitationRepo;
    private final OrganisationMemberRepository memberRepo;

    public AuthService(UserRepository repo, PasswordEncoder encoder, JwtUtil jwtUtil,
                       ClientRepository clientRepository, AuditorRepository auditorRepository,
                       EmailService emailService, OtpRepository otpVerificationRepository,
                       OrganisationInvitationRepository invitationRepo,
                       OrganisationMemberRepository memberRepo) {
        this.repo = repo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
        this.clientRepository = clientRepository;
        this.auditorRepository = auditorRepository;
        this.emailService = emailService;
        this.otpVerificationRepository = otpVerificationRepository;
        this.invitationRepo = invitationRepo;
        this.memberRepo = memberRepo;
    }

    public Mono<ResponseMessage> registerUser(UserRegister request) {
        if (request.getRole() == Role.MEMBER) {
            return Mono.just(new ResponseMessage(HttpStatus.FORBIDDEN,
                    "Member accounts can only be created through organisation invitation"));
        }
        if (request.getRole() == Role.ADMIN) {
            return Mono.just(new ResponseMessage(HttpStatus.FORBIDDEN,
                    "Admin accounts cannot be self-registered"));
        }

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
                .flatMap(this::validateAuthProvider)
                .flatMap(user -> validatePassword(user, request.getPassword()))
                .flatMap(this::validateRoleAccess)
                .flatMap(user -> processInviteToken(user, request.getInviteToken()))
                .flatMap(this::generateTokenResponse);
    }

    private Mono<User> processInviteToken(User user, String inviteToken) {
        boolean hasToken = inviteToken != null && !inviteToken.isBlank();

        if (!hasToken && !user.isMustChangePassword()) {
            return Mono.just(user);
        }

        if (!hasToken) {
            return Mono.error(new InvalidRecord(
                    "An invitation token is required for your first login. Please use the link from your invitation email."));
        }

        return invitationRepo.findByToken(inviteToken)
                .switchIfEmpty(Mono.error(new InvalidRecord(
                        "Invitation not found. Please check your invitation email or contact the organisation owner.")))
                .flatMap(inv -> {
                    if (!inv.getInvitedEmail().equalsIgnoreCase(user.getUsername())) {
                        return Mono.error(new InvalidRecord(
                                "This invitation does not belong to your account."));
                    }
                    if (inv.getStatus() == InvitationStatus.REVOKED) {
                        return Mono.error(new InvalidRecord(
                                "This invitation has been revoked. Please contact the organisation owner."));
                    }
                    if (inv.getStatus() == InvitationStatus.ACCEPTED) {
                        return Mono.error(new InvalidRecord(
                                "This invitation has already been accepted."));
                    }
                    if (inv.getStatus() == InvitationStatus.EXPIRED
                            || inv.getExpiresAt().isBefore(LocalDateTime.now())) {
                        inv.setStatus(InvitationStatus.EXPIRED);
                        return invitationRepo.save(inv)
                                .then(Mono.error(new InvalidRecord(
                                        "Your invitation has expired. Please contact the organisation owner for a new invitation.")));
                    }

                    // Valid PENDING invitation — accept it and activate the specific org membership.
                    inv.setStatus(InvitationStatus.ACCEPTED);
                    boolean isFirstLogin = user.isMustChangePassword();

                    return invitationRepo.save(inv)
                            .then(memberRepo.findByOrganisationIdAndUserId(inv.getOrganisationId(), user.getId()))
                            .switchIfEmpty(Mono.error(new InvalidRecord(
                                    "Organisation membership record not found. Please contact the organisation owner.")))
                            .flatMap(member -> {
                                member.setStatus(MemberStatus.ACTIVE);
                                return memberRepo.save(member);
                            })
                            .then(isFirstLogin
                                    ? Mono.fromRunnable(() ->
                                            emailService.sendPasswordChangeReminderEmail(
                                                    user.getUsername(), user.getFullName()))
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .thenReturn(user)
                                    : Mono.just(user));
                });
    }

    public Mono<ResponseMessage> changePassword(String email, ChangePasswordRequest request) {
        return repo.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("User not found")))
                .flatMap(user -> {
                    if (!encoder.matches(request.getCurrentPassword(), user.getPassword())) {
                        return Mono.error(new InvalidRecord("Current password is incorrect"));
                    }
                    user.setPassword(encoder.encode(request.getNewPassword()));
                    user.setMustChangePassword(false);
                    return repo.save(user)
                            .then(Mono.fromRunnable(() ->
                                    emailService.sendPasswordChangedEmail(user.getUsername(), user.getFullName()))
                                    .subscribeOn(Schedulers.boundedElastic()))
                            .thenReturn(new ResponseMessage(HttpStatus.OK, "Password changed successfully"));
                });
    }

    private Mono<User> validateAuthProvider(User user) {
        if (!"JWT".equals(user.getAuthProvider())) {
            return Mono.error(new InvalidRecord(
                    "This account uses social login. Please sign in with " + user.getAuthProvider() + "."));
        }
        return Mono.just(user);
    }

    private Mono<User> validatePassword(User user, String rawPassword) {
        if (!encoder.matches(rawPassword, user.getPassword())) {
            return Mono.error(new InvalidRecord("Invalid credentials"));
        }
        return Mono.just(user);
    }

    private Mono<User> validateRoleAccess(User user) {
        return switch (user.getRole()) {
            case CLIENT -> otpVerificationRepository.findByEmail(user.getUsername())
                    .switchIfEmpty(Mono.error(new InvalidRecord(
                            "Your account is not active. Please verify your email using the OTP.")))
                    .flatMap(otp -> {
                        if (otp.isVerified()) return Mono.just(user);
                        if (otp.getExpiry().isBefore(LocalDateTime.now())) {
                            return Mono.error(new InvalidRecord("Your OTP has expired. Please request a new one."));
                        }
                        return Mono.error(new InvalidRecord(
                                "Your account is not active. Please verify your email using the OTP."));
                    });
            case AUDITOR -> user.isVerified()
                    ? Mono.just(user)
                    : Mono.error(new InvalidRecord("Your account is waiting for admin approval."));
            case ADMIN -> Mono.just(user);
            // MEMBER accounts are created by invitation with verified=true — no extra check needed.
            case MEMBER -> Mono.just(user);
            default -> Mono.error(new InvalidRecord("Unknown user role"));
        };
    }

    private Mono<LoginMessage> generateTokenResponse(User user) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return Mono.just(new LoginMessage(HttpStatus.OK, "Successfully Login", token,
                user.getRole(), user.isMustChangePassword()));
    }


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
                            .then(repo.findByUsername(otp.getEmail())
                                    .flatMap(user -> {
                                        user.setVerified(true);
                                        return repo.save(user);
                                    })
                                    .then())
                            .thenReturn(new ResponseMessage(HttpStatus.OK, "Successfully verified. Account Activated"));
                });
    }


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
                            .switchIfEmpty(Mono.defer(() ->
                                    generateOtp(email)
                                            .flatMap(newOtp ->
                                                    Mono.fromRunnable(() -> emailService.sendVerificationEmail(
                                                                    email, user.getFullName(), newOtp))
                                                            .subscribeOn(Schedulers.boundedElastic())
                                                            .thenReturn(new ResponseMessage(HttpStatus.OK,
                                                                    "A new OTP has been sent to your email."))
                                            ))
                            );
                });
    }
}