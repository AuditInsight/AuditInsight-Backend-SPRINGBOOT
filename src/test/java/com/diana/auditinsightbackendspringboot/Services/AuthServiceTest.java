package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Authentication.JwtUtil;
import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.AuditorProfile;
import com.diana.auditinsightbackendspringboot.Models.ClientProfile;
import com.diana.auditinsightbackendspringboot.Models.OtpVerification;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.diana.auditinsightbackendspringboot.Enum.InvitationStatus;
import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
import com.diana.auditinsightbackendspringboot.Models.OrganisationInvitation;
import com.diana.auditinsightbackendspringboot.Models.OrganisationMember;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private AuditorRepository auditorRepository;
    @Mock private EmailService emailService;
    @Mock private OtpRepository otpRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private OrganisationInvitationRepository invitationRepository;
    @Mock private OrganisationMemberRepository memberRepository;

    private AuthService authService;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, encoder, jwtUtil,
                clientRepository, auditorRepository,
                emailService, otpRepository,
                invitationRepository, memberRepository
        );
    }

    // ──────────────────────────── registerUser ────────────────────────────

    @Test
    void registerUser_newClient_returns201AndSendsOtp() {
        when(userRepository.existsByUsername("alice@test.com")).thenReturn(Mono.just(false));

        User saved = user("alice@test.com", Role.CLIENT);
        saved.setId(1L);
        when(userRepository.save(any())).thenReturn(Mono.just(saved));

        ClientProfile savedProfile = new ClientProfile();
        when(clientRepository.save(any())).thenReturn(Mono.just(savedProfile));

        when(otpRepository.deleteByEmail("alice@test.com")).thenReturn(Mono.empty());
        OtpVerification otp = new OtpVerification();
        otp.setOtp("123456");
        otp.setEmail("alice@test.com");
        otp.setExpiry(LocalDateTime.now().plusMinutes(10));
        when(otpRepository.save(any())).thenReturn(Mono.just(otp));

        StepVerifier.create(authService.registerUser(clientRegisterRequest("alice@test.com")))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.CREATED
                        && r.getMessage().contains("OTP"))
                .verifyComplete();
    }

    @Test
    void registerUser_memberRole_returnsForbidden() {
        UserRegister req = new UserRegister();
        req.setFirstName("Eve");
        req.setLastName("Doe");
        req.setUsername("eve@test.com");
        req.setPassword("Password1@");
        req.setRole(Role.MEMBER);

        StepVerifier.create(authService.registerUser(req))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.FORBIDDEN
                        && r.getMessage().contains("invitation"))
                .verifyComplete();
    }

    @Test
    void registerUser_existingEmail_returnsConflict() {
        when(userRepository.existsByUsername("alice@test.com")).thenReturn(Mono.just(true));

        StepVerifier.create(authService.registerUser(clientRegisterRequest("alice@test.com")))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.CONFLICT)
                .verifyComplete();
    }

    @Test
    void registerUser_newAuditor_returns201AndSendsOtp() {
        when(userRepository.existsByUsername("bob@test.com")).thenReturn(Mono.just(false));

        User saved = user("bob@test.com", Role.AUDITOR);
        saved.setId(2L);
        when(userRepository.save(any())).thenReturn(Mono.just(saved));

        AuditorProfile savedProfile = new AuditorProfile();
        when(auditorRepository.save(any())).thenReturn(Mono.just(savedProfile));

        when(otpRepository.deleteByEmail("bob@test.com")).thenReturn(Mono.empty());
        OtpVerification otp = new OtpVerification();
        otp.setOtp("123456");
        otp.setEmail("bob@test.com");
        otp.setExpiry(LocalDateTime.now().plusMinutes(10));
        when(otpRepository.save(any())).thenReturn(Mono.just(otp));

        StepVerifier.create(authService.registerUser(auditorRegisterRequest("bob@test.com")))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.CREATED
                        && r.getMessage().contains("OTP"))
                .verifyComplete();
    }

    // ──────────────────────────── login ────────────────────────────

    @Test
    void login_validVerifiedClient_returnsToken() {
        User u = user("alice@test.com", Role.CLIENT);
        u.setPassword(encoder.encode("Password1@"));
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));

        OtpVerification otp = new OtpVerification();
        otp.setVerified(true);
        when(otpRepository.findByEmail("alice@test.com")).thenReturn(Mono.just(otp));
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mocked.jwt.token");

        LoginRequest req = new LoginRequest();
        req.setUsername("alice@test.com");
        req.setPassword("Password1@");

        StepVerifier.create(authService.login(req))
                .expectNextMatches(r -> r.getToken().equals("mocked.jwt.token")
                        && r.getRole() == Role.CLIENT
                        && !r.isMustChangePassword())
                .verifyComplete();
    }

    @Test
    void login_invitedMember_withValidToken_activatesMembershipAndReturnsMustChangePassword() {
        UUID orgId = UUID.randomUUID();
        String token = "valid-invite-token";

        User u = user("eve@test.com", Role.MEMBER);
        u.setPassword(encoder.encode("TempPass1@"));
        u.setVerified(true);
        u.setMustChangePassword(true);
        when(userRepository.findByUsername("eve@test.com")).thenReturn(Mono.just(u));

        OrganisationInvitation inv = invitation(orgId, "eve@test.com", token, InvitationStatus.PENDING,
                LocalDateTime.now().plusHours(48));
        when(invitationRepository.findByToken(token)).thenReturn(Mono.just(inv));
        when(invitationRepository.save(any())).thenReturn(Mono.just(inv));

        OrganisationMember member = new OrganisationMember();
        member.setStatus(MemberStatus.PENDING);
        when(memberRepository.findByOrganisationIdAndUserId(orgId, null)).thenReturn(Mono.just(member));
        when(memberRepository.save(any())).thenReturn(Mono.just(member));

        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("member.jwt.token");

        LoginRequest req = new LoginRequest();
        req.setUsername("eve@test.com");
        req.setPassword("TempPass1@");
        req.setInviteToken(token);

        StepVerifier.create(authService.login(req))
                .expectNextMatches(r -> r.getToken().equals("member.jwt.token")
                        && r.getRole() == Role.MEMBER
                        && r.isMustChangePassword())
                .verifyComplete();
    }

    @Test
    void login_invitedMember_withoutToken_returnsError() {
        User u = user("eve@test.com", Role.MEMBER);
        u.setPassword(encoder.encode("TempPass1@"));
        u.setVerified(true);
        u.setMustChangePassword(true);
        when(userRepository.findByUsername("eve@test.com")).thenReturn(Mono.just(u));

        LoginRequest req = new LoginRequest();
        req.setUsername("eve@test.com");
        req.setPassword("TempPass1@");
        // no inviteToken

        StepVerifier.create(authService.login(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("invitation token is required"))
                .verify();
    }

    @Test
    void login_invitedMember_withExpiredToken_returnsError() {
        UUID orgId = UUID.randomUUID();
        String token = "expired-token";

        User u = user("eve@test.com", Role.MEMBER);
        u.setPassword(encoder.encode("TempPass1@"));
        u.setVerified(true);
        u.setMustChangePassword(true);
        when(userRepository.findByUsername("eve@test.com")).thenReturn(Mono.just(u));

        OrganisationInvitation inv = invitation(orgId, "eve@test.com", token, InvitationStatus.PENDING,
                LocalDateTime.now().minusHours(1)); // already expired
        when(invitationRepository.findByToken(token)).thenReturn(Mono.just(inv));
        when(invitationRepository.save(any())).thenReturn(Mono.just(inv));

        LoginRequest req = new LoginRequest();
        req.setUsername("eve@test.com");
        req.setPassword("TempPass1@");
        req.setInviteToken(token);

        StepVerifier.create(authService.login(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("expired"))
                .verify();
    }

    @Test
    void login_wrongPassword_emitsInvalidRecord() {
        User u = user("alice@test.com", Role.CLIENT);
        u.setPassword(encoder.encode("RightPass1@"));
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));

        LoginRequest req = new LoginRequest();
        req.setUsername("alice@test.com");
        req.setPassword("WrongPass1@");

        StepVerifier.create(authService.login(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().equals("Invalid credentials"))
                .verify();
    }

    @Test
    void login_unknownUser_emitsInvalidRecord() {
        when(userRepository.findByUsername("nobody@test.com")).thenReturn(Mono.empty());

        LoginRequest req = new LoginRequest();
        req.setUsername("nobody@test.com");
        req.setPassword("Password1@");

        StepVerifier.create(authService.login(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().equals("Username not found"))
                .verify();
    }

    @Test
    void login_socialLoginUser_emitsInvalidRecord() {
        User u = user("alice@test.com", Role.CLIENT);
        u.setAuthProvider("google");
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));

        LoginRequest req = new LoginRequest();
        req.setUsername("alice@test.com");
        req.setPassword("Password1@");

        StepVerifier.create(authService.login(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("social login"))
                .verify();
    }

    @Test
    void login_unverifiedClient_emitsInvalidRecord() {
        User u = user("alice@test.com", Role.CLIENT);
        u.setPassword(encoder.encode("Password1@"));
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));

        OtpVerification otp = new OtpVerification();
        otp.setVerified(false);
        otp.setExpiry(LocalDateTime.now().plusMinutes(5));
        when(otpRepository.findByEmail("alice@test.com")).thenReturn(Mono.just(otp));

        LoginRequest req = new LoginRequest();
        req.setUsername("alice@test.com");
        req.setPassword("Password1@");

        StepVerifier.create(authService.login(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("not active"))
                .verify();
    }

    @Test
    void login_expiredOtp_emitsInvalidRecord() {
        User u = user("alice@test.com", Role.CLIENT);
        u.setPassword(encoder.encode("Password1@"));
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));

        OtpVerification otp = new OtpVerification();
        otp.setVerified(false);
        otp.setExpiry(LocalDateTime.now().minusMinutes(1));
        when(otpRepository.findByEmail("alice@test.com")).thenReturn(Mono.just(otp));

        LoginRequest req = new LoginRequest();
        req.setUsername("alice@test.com");
        req.setPassword("Password1@");

        StepVerifier.create(authService.login(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("expired"))
                .verify();
    }

    @Test
    void login_unverifiedAuditor_emitsInvalidRecord() {
        User u = user("bob@test.com", Role.AUDITOR);
        u.setPassword(encoder.encode("Password1@"));
        when(userRepository.findByUsername("bob@test.com")).thenReturn(Mono.just(u));

        OtpVerification otp = new OtpVerification();
        otp.setVerified(false);
        otp.setExpiry(LocalDateTime.now().plusMinutes(5));
        when(otpRepository.findByEmail("bob@test.com")).thenReturn(Mono.just(otp));

        LoginRequest req = new LoginRequest();
        req.setUsername("bob@test.com");
        req.setPassword("Password1@");

        StepVerifier.create(authService.login(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("not active"))
                .verify();
    }

    @Test
    void login_deactivatedAccount_emitsInvalidRecord() {
        User u = user("alice@test.com", Role.CLIENT);
        u.setPassword(encoder.encode("Password1@"));
        u.setActive(false);
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));

        LoginRequest req = new LoginRequest();
        req.setUsername("alice@test.com");
        req.setPassword("Password1@");

        StepVerifier.create(authService.login(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("deactivated"))
                .verify();
    }

    @Test
    void login_adminUser_returnsToken() {
        User u = user("admin@test.com", Role.ADMIN);
        u.setPassword(encoder.encode("AdminPass1@"));
        when(userRepository.findByUsername("admin@test.com")).thenReturn(Mono.just(u));
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("admin.jwt.token");

        LoginRequest req = new LoginRequest();
        req.setUsername("admin@test.com");
        req.setPassword("AdminPass1@");

        StepVerifier.create(authService.login(req))
                .expectNextMatches(r -> r.getRole() == Role.ADMIN
                        && r.getToken().equals("admin.jwt.token"))
                .verifyComplete();
    }

    @Test
    void login_verifiedAuditor_returnsToken() {
        User u = user("bob@test.com", Role.AUDITOR);
        u.setPassword(encoder.encode("Password1@"));
        when(userRepository.findByUsername("bob@test.com")).thenReturn(Mono.just(u));

        OtpVerification otp = new OtpVerification();
        otp.setVerified(true);
        when(otpRepository.findByEmail("bob@test.com")).thenReturn(Mono.just(otp));
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("auditor.jwt.token");

        LoginRequest req = new LoginRequest();
        req.setUsername("bob@test.com");
        req.setPassword("Password1@");

        StepVerifier.create(authService.login(req))
                .expectNextMatches(r -> r.getRole() == Role.AUDITOR
                        && r.getToken().equals("auditor.jwt.token"))
                .verifyComplete();
    }

    // ──────────────────────────── verifyOtp ────────────────────────────

    @Test
    void verifyOtp_validOtp_setsVerifiedAndReturnsSuccess() {
        OtpVerification otp = new OtpVerification();
        otp.setEmail("alice@test.com");
        otp.setOtp("123456");
        otp.setVerified(false);
        otp.setExpiry(LocalDateTime.now().plusMinutes(5));

        when(otpRepository.findByEmailAndOtp("alice@test.com", "123456")).thenReturn(Mono.just(otp));
        OtpVerification verifiedOtp = new OtpVerification();
        verifiedOtp.setVerified(true);
        when(otpRepository.save(any())).thenReturn(Mono.just(verifiedOtp));

        User u = user("alice@test.com", Role.CLIENT);
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));
        when(userRepository.save(any())).thenReturn(Mono.just(u));

        OtpRequest req = new OtpRequest();
        req.setEmail("alice@test.com");
        req.setOtp("123456");

        StepVerifier.create(authService.verifyOtp(req))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("verified"))
                .verifyComplete();
    }

    @Test
    void verifyOtp_wrongOtp_emitsInvalidRecord() {
        when(otpRepository.findByEmailAndOtp("alice@test.com", "000000")).thenReturn(Mono.empty());

        OtpRequest req = new OtpRequest();
        req.setEmail("alice@test.com");
        req.setOtp("000000");

        StepVerifier.create(authService.verifyOtp(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord)
                .verify();
    }

    @Test
    void verifyOtp_alreadyVerified_emitsInvalidRecord() {
        OtpVerification otp = new OtpVerification();
        otp.setVerified(true);
        otp.setExpiry(LocalDateTime.now().plusMinutes(5));
        when(otpRepository.findByEmailAndOtp("alice@test.com", "123456")).thenReturn(Mono.just(otp));

        OtpRequest req = new OtpRequest();
        req.setEmail("alice@test.com");
        req.setOtp("123456");

        StepVerifier.create(authService.verifyOtp(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("already verified"))
                .verify();
    }

    @Test
    void verifyOtp_expiredOtp_emitsInvalidRecord() {
        OtpVerification otp = new OtpVerification();
        otp.setVerified(false);
        otp.setExpiry(LocalDateTime.now().minusMinutes(1));
        when(otpRepository.findByEmailAndOtp("alice@test.com", "123456")).thenReturn(Mono.just(otp));

        OtpRequest req = new OtpRequest();
        req.setEmail("alice@test.com");
        req.setOtp("123456");

        StepVerifier.create(authService.verifyOtp(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("expired"))
                .verify();
    }

    // ──────────────────────────── resendOtp ────────────────────────────

    @Test
    void resendOtp_unverifiedClient_sendsNewOtpAndReturns200() {
        User u = user("alice@test.com", Role.CLIENT);
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));

        OtpVerification existing = new OtpVerification();
        existing.setVerified(false);
        existing.setExpiry(LocalDateTime.now().minusMinutes(1));
        when(otpRepository.findByEmail("alice@test.com")).thenReturn(Mono.just(existing));

        when(otpRepository.deleteByEmail("alice@test.com")).thenReturn(Mono.empty());
        OtpVerification newOtp = new OtpVerification();
        newOtp.setOtp("654321");
        newOtp.setEmail("alice@test.com");
        newOtp.setExpiry(LocalDateTime.now().plusMinutes(10));
        when(otpRepository.save(any())).thenReturn(Mono.just(newOtp));

        StepVerifier.create(authService.resendOtp("alice@test.com"))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("new OTP"))
                .verifyComplete();
    }

    @Test
    void resendOtp_alreadyVerifiedClient_returns200WithAlreadyVerifiedMessage() {
        User u = user("alice@test.com", Role.CLIENT);
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));

        OtpVerification existing = new OtpVerification();
        existing.setVerified(true);
        when(otpRepository.findByEmail("alice@test.com")).thenReturn(Mono.just(existing));

        StepVerifier.create(authService.resendOtp("alice@test.com"))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("already verified"))
                .verifyComplete();
    }

    @Test
    void resendOtp_unknownEmail_emitsInvalidRecord() {
        when(userRepository.findByUsername("nobody@test.com")).thenReturn(Mono.empty());

        StepVerifier.create(authService.resendOtp("nobody@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("No account"))
                .verify();
    }

    @Test
    void resendOtp_unverifiedAuditor_sendsNewOtpAndReturns200() {
        User auditor = user("bob@test.com", Role.AUDITOR);
        when(userRepository.findByUsername("bob@test.com")).thenReturn(Mono.just(auditor));

        OtpVerification existing = new OtpVerification();
        existing.setVerified(false);
        existing.setExpiry(LocalDateTime.now().minusMinutes(1));
        when(otpRepository.findByEmail("bob@test.com")).thenReturn(Mono.just(existing));

        when(otpRepository.deleteByEmail("bob@test.com")).thenReturn(Mono.empty());
        OtpVerification newOtp = new OtpVerification();
        newOtp.setOtp("654321");
        newOtp.setEmail("bob@test.com");
        newOtp.setExpiry(LocalDateTime.now().plusMinutes(10));
        when(otpRepository.save(any())).thenReturn(Mono.just(newOtp));

        StepVerifier.create(authService.resendOtp("bob@test.com"))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("new OTP"))
                .verifyComplete();
    }

    @Test
    void resendOtp_adminAccount_emitsInvalidRecord() {
        User admin = user("admin@test.com", Role.ADMIN);
        when(userRepository.findByUsername("admin@test.com")).thenReturn(Mono.just(admin));

        StepVerifier.create(authService.resendOtp("admin@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("only required for client and auditor"))
                .verify();
    }

    // ──────────────────────────── forgotPassword ────────────────────────────

    @Test
    void forgotPassword_deactivatedAccount_stillSendsResetOtp() {
        User u = user("alice@test.com", Role.CLIENT);
        u.setActive(false);
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));

        when(otpRepository.deleteByEmail("alice@test.com")).thenReturn(Mono.empty());
        OtpVerification otp = new OtpVerification();
        otp.setOtp("123456");
        otp.setEmail("alice@test.com");
        otp.setExpiry(LocalDateTime.now().plusMinutes(10));
        when(otpRepository.save(any())).thenReturn(Mono.just(otp));

        StepVerifier.create(authService.forgotPassword("alice@test.com"))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("reset code has been sent"))
                .verifyComplete();
    }

    @Test
    void forgotPassword_unknownEmail_returnsGenericSuccessMessage() {
        when(userRepository.findByUsername("nobody@test.com")).thenReturn(Mono.empty());

        StepVerifier.create(authService.forgotPassword("nobody@test.com"))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("reset code has been sent"))
                .verifyComplete();
    }

    // ──────────────────────────── resetPassword ────────────────────────────

    @Test
    void resetPassword_validOtp_updatesPasswordAndReturnsSuccess() {
        OtpVerification otp = new OtpVerification();
        otp.setEmail("alice@test.com");
        otp.setOtp("123456");
        otp.setVerified(false);
        otp.setExpiry(LocalDateTime.now().plusMinutes(5));
        when(otpRepository.findByEmailAndOtp("alice@test.com", "123456")).thenReturn(Mono.just(otp));
        when(otpRepository.save(any())).thenReturn(Mono.just(otp));

        User u = user("alice@test.com", Role.CLIENT);
        u.setActive(false);
        when(userRepository.findByUsername("alice@test.com")).thenReturn(Mono.just(u));
        when(userRepository.save(any())).thenReturn(Mono.just(u));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("alice@test.com");
        req.setOtp("123456");
        req.setNewPassword("NewPassword1@");

        StepVerifier.create(authService.resetPassword(req))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("Password reset successfully"))
                .verifyComplete();
    }

    @Test
    void resetPassword_invalidOtp_emitsInvalidRecord() {
        when(otpRepository.findByEmailAndOtp("alice@test.com", "000000")).thenReturn(Mono.empty());

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("alice@test.com");
        req.setOtp("000000");
        req.setNewPassword("NewPassword1@");

        StepVerifier.create(authService.resetPassword(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("Invalid OTP"))
                .verify();
    }

    @Test
    void resetPassword_expiredOtp_emitsInvalidRecord() {
        OtpVerification otp = new OtpVerification();
        otp.setVerified(false);
        otp.setExpiry(LocalDateTime.now().minusMinutes(1));
        when(otpRepository.findByEmailAndOtp("alice@test.com", "123456")).thenReturn(Mono.just(otp));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("alice@test.com");
        req.setOtp("123456");
        req.setNewPassword("NewPassword1@");

        StepVerifier.create(authService.resetPassword(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("expired"))
                .verify();
    }

    @Test
    void resetPassword_alreadyUsedOtp_emitsInvalidRecord() {
        OtpVerification otp = new OtpVerification();
        otp.setVerified(true);
        otp.setExpiry(LocalDateTime.now().plusMinutes(5));
        when(otpRepository.findByEmailAndOtp("alice@test.com", "123456")).thenReturn(Mono.just(otp));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("alice@test.com");
        req.setOtp("123456");
        req.setNewPassword("NewPassword1@");

        StepVerifier.create(authService.resetPassword(req))
                .expectErrorMatches(e -> e instanceof InvalidRecord
                        && e.getMessage().contains("already been used"))
                .verify();
    }

    // ──────────────────────────── helpers ────────────────────────────

    private User user(String email, Role role) {
        User u = new User();
        u.setUsername(email);
        u.setFullName("Test User");
        u.setRole(role);
        u.setAuthProvider("JWT");
        return u;
    }

    private OrganisationInvitation invitation(UUID orgId, String email, String token,
                                              InvitationStatus status, LocalDateTime expiresAt) {
        OrganisationInvitation inv = new OrganisationInvitation();
        inv.setOrganisationId(orgId);
        inv.setInvitedEmail(email);
        inv.setToken(token);
        inv.setStatus(status);
        inv.setExpiresAt(expiresAt);
        inv.setCreatedAt(LocalDateTime.now());
        return inv;
    }

    private UserRegister clientRegisterRequest(String email) {
        UserRegister r = new UserRegister();
        r.setFirstName("Alice");
        r.setLastName("Smith");
        r.setUsername(email);
        r.setPassword("Password1@");
        r.setRole(Role.CLIENT);
        return r;
    }

    private UserRegister auditorRegisterRequest(String email) {
        UserRegister r = new UserRegister();
        r.setFirstName("Bob");
        r.setLastName("Jones");
        r.setUsername(email);
        r.setPassword("Password1@");
        r.setRole(Role.AUDITOR);
        return r;
    }
}