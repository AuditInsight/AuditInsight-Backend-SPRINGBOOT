package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Enum.*;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.*;
import com.diana.auditinsightbackendspringboot.Repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganisationServiceTest {

    @Mock private OrganisationRepository orgRepo;
    @Mock private OrganisationMemberRepository memberRepo;
    @Mock private OrganisationCurrencyRepository currencyRepo;
    @Mock private OrganisationInvitationRepository invitationRepo;
    @Mock private UserRepository userRepo;
    @Mock private ClientRepository clientRepo;
    @Mock private EmailService emailService;

    private OrganisationService service;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private final UUID ORG_ID  = UUID.randomUUID();
    private final UUID INV_ID  = UUID.randomUUID();
    private final UUID CP_ID   = UUID.randomUUID(); // client_profile.id

    @BeforeEach
    void setUp() {
        service = new OrganisationService(
                orgRepo, memberRepo, currencyRepo, invitationRepo,
                userRepo, clientRepo, emailService, encoder);
    }

    // ──────────────────────────── createOrganisation ────────────────────────

    @Test
    void createOrganisation_newOrg_returnsResponse() {
        User owner = user(1L, "owner@test.com", Role.CLIENT);
        when(userRepo.findByUsername("owner@test.com")).thenReturn(Mono.just(owner));

        ClientProfile cp = clientProfile(CP_ID, "owner@test.com");
        when(clientRepo.findByEmailAddress("owner@test.com")).thenReturn(Mono.just(cp));

        Organisation saved = org(ORG_ID, CP_ID, "Acme");
        when(orgRepo.save(any())).thenReturn(Mono.just(saved));
        when(memberRepo.save(any())).thenReturn(Mono.just(new OrganisationMember()));
        when(currencyRepo.save(any())).thenReturn(Mono.just(new OrganisationCurrency()));
        when(currencyRepo.findAllByOrganisationId(ORG_ID)).thenReturn(Flux.just(currency(ORG_ID, "USD")));

        CreateOrganisationRequest req = new CreateOrganisationRequest();
        req.setName("Acme");
        req.setIndustry("Finance");
        req.setFiscalYearStart("01-01");
        req.setFiscalYearEnd("12-31");
        req.setCurrencies(List.of("USD"));

        StepVerifier.create(service.createOrganisation("owner@test.com", req))
                .expectNextMatches(r -> "Acme".equals(r.getName()) && r.getCurrencies().contains("USD"))
                .verifyComplete();
    }

    @Test
    void createOrganisation_nonClientUser_returnsError() {
        User auditor = user(2L, "auditor@test.com", Role.AUDITOR);
        when(userRepo.findByUsername("auditor@test.com")).thenReturn(Mono.just(auditor));

        CreateOrganisationRequest req = new CreateOrganisationRequest();
        req.setName("Org");
        req.setFiscalYearStart("01-01");
        req.setFiscalYearEnd("12-31");
        req.setCurrencies(List.of("USD"));

        StepVerifier.create(service.createOrganisation("auditor@test.com", req))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("CLIENT"))
                .verify();
    }

    // ──────────────────────────── listMyOrganisations ────────────────────────

    @Test
    void listMyOrganisations_returnsAllOrgsUserBelongsTo() {
        User owner = user(1L, "owner@test.com", Role.CLIENT);
        when(userRepo.findByUsername("owner@test.com")).thenReturn(Mono.just(owner));

        OrganisationMember membership = member(ORG_ID, 1L, Role.CLIENT);
        when(memberRepo.findAllByUserId(1L)).thenReturn(Flux.just(membership));

        Organisation org = org(ORG_ID, CP_ID, "Acme");
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.just(org));

        StepVerifier.create(service.listMyOrganisations("owner@test.com"))
                .expectNextMatches(r -> "Acme".equals(r.getName()))
                .verifyComplete();
    }

    // ──────────────────────────── getOrganisation ────────────────────────────

    @Test
    void getOrganisation_memberCanView() {
        User member = user(2L, "member@test.com", Role.MEMBER);
        when(userRepo.findByUsername("member@test.com")).thenReturn(Mono.just(member));

        OrganisationMember m = member(ORG_ID, 2L, Role.AUDITOR);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 2L)).thenReturn(Mono.just(m));

        Organisation org = org(ORG_ID, CP_ID, "Acme");
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.just(org));
        when(currencyRepo.findAllByOrganisationId(ORG_ID)).thenReturn(Flux.empty());

        StepVerifier.create(service.getOrganisation(ORG_ID, "member@test.com"))
                .expectNextMatches(r -> "Acme".equals(r.getName()))
                .verifyComplete();
    }

    @Test
    void getOrganisation_nonMemberGetsError() {
        User outsider = user(3L, "outsider@test.com", Role.CLIENT);
        when(userRepo.findByUsername("outsider@test.com")).thenReturn(Mono.just(outsider));
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 3L)).thenReturn(Mono.empty());
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.getOrganisation(ORG_ID, "outsider@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("not a member"))
                .verify();
    }

    @Test
    void getOrganisation_pendingMemberGetsError() {
        User pending = user(4L, "pending@test.com", Role.MEMBER);
        when(userRepo.findByUsername("pending@test.com")).thenReturn(Mono.just(pending));

        OrganisationMember pendingMember = member(ORG_ID, 4L, Role.MEMBER);
        pendingMember.setStatus(MemberStatus.PENDING);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 4L)).thenReturn(Mono.just(pendingMember));
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.getOrganisation(ORG_ID, "pending@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("pending"))
                .verify();
    }

    // ──────────────────────────── updateOrganisation ─────────────────────────

    @Test
    void updateOrganisation_ownerCanUpdate() {
        User owner = user(1L, "owner@test.com", Role.CLIENT);
        when(userRepo.findByUsername("owner@test.com")).thenReturn(Mono.just(owner));

        OrganisationMember ownerMember = member(ORG_ID, 1L, Role.CLIENT);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 1L)).thenReturn(Mono.just(ownerMember));

        Organisation org = org(ORG_ID, CP_ID, "Acme");
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.just(org));
        when(orgRepo.save(any())).thenReturn(Mono.just(org));
        when(currencyRepo.findAllByOrganisationId(ORG_ID)).thenReturn(Flux.empty());

        UpdateOrganisationRequest req = new UpdateOrganisationRequest();
        req.setName("Acme Updated");

        StepVerifier.create(service.updateOrganisation(ORG_ID, "owner@test.com", req))
                .expectNextMatches(r -> r.getMessage().contains("updated"))
                .verifyComplete();
    }

    @Test
    void updateOrganisation_nonOwnerGetsError() {
        User member = user(2L, "member@test.com", Role.MEMBER);
        when(userRepo.findByUsername("member@test.com")).thenReturn(Mono.just(member));

        OrganisationMember m = member(ORG_ID, 2L, Role.AUDITOR);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 2L)).thenReturn(Mono.just(m));
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.updateOrganisation(ORG_ID, "member@test.com", new UpdateOrganisationRequest()))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("Only the owner"))
                .verify();
    }

    // ──────────────────────────── inviteMember ───────────────────────────────

    @Test
    void inviteMember_existingUser_addedAsPendingWithInvitationEmail() {
        User requester = user(1L, "owner@test.com", Role.CLIENT);
        when(userRepo.findByUsername("owner@test.com")).thenReturn(Mono.just(requester));

        OrganisationMember ownerMember = member(ORG_ID, 1L, Role.CLIENT);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 1L)).thenReturn(Mono.just(ownerMember));

        ClientProfile cp = clientProfile(CP_ID, "owner@test.com");
        when(clientRepo.findByEmailAddress("owner@test.com")).thenReturn(Mono.just(cp));

        Organisation org = org(ORG_ID, CP_ID, "Acme");
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.just(org));

        User invitee = user(2L, "newmember@test.com", Role.AUDITOR);
        when(userRepo.findByUsername("newmember@test.com")).thenReturn(Mono.just(invitee));
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 2L)).thenReturn(Mono.empty());
        when(memberRepo.save(any())).thenReturn(Mono.just(new OrganisationMember()));
        when(invitationRepo.save(any())).thenReturn(Mono.just(invitation(INV_ID, ORG_ID, "newmember@test.com")));

        InviteMemberRequest req = new InviteMemberRequest();
        req.setEmail("newmember@test.com");
        req.setRole(Role.AUDITOR);

        StepVerifier.create(service.inviteMember(ORG_ID, "owner@test.com", req))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("Invitation sent"))
                .verifyComplete();
    }

    @Test
    void inviteMember_newUser_createsAccountAndSendsCredentials() {
        User requester = user(1L, "owner@test.com", Role.CLIENT);
        when(userRepo.findByUsername("owner@test.com")).thenReturn(Mono.just(requester));

        OrganisationMember ownerMember = member(ORG_ID, 1L, Role.CLIENT);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 1L)).thenReturn(Mono.just(ownerMember));

        ClientProfile cp = clientProfile(CP_ID, "owner@test.com");
        when(clientRepo.findByEmailAddress("owner@test.com")).thenReturn(Mono.just(cp));

        Organisation org = org(ORG_ID, CP_ID, "Acme");
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.just(org));

        when(userRepo.findByUsername("unknown@test.com")).thenReturn(Mono.empty());

        User createdUser = user(3L, "unknown@test.com", Role.MEMBER);
        when(userRepo.save(any())).thenReturn(Mono.just(createdUser));
        when(memberRepo.save(any())).thenReturn(Mono.just(new OrganisationMember()));

        OrganisationInvitation saved = invitation(INV_ID, ORG_ID, "unknown@test.com");
        when(invitationRepo.save(any())).thenReturn(Mono.just(saved));

        InviteMemberRequest req = new InviteMemberRequest();
        req.setEmail("unknown@test.com");
        req.setRole(Role.MEMBER);

        StepVerifier.create(service.inviteMember(ORG_ID, "owner@test.com", req))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK
                        && r.getMessage().contains("Account created"))
                .verifyComplete();
    }

    @Test
    void inviteMember_clientRoleBlocked() {
        InviteMemberRequest req = new InviteMemberRequest();
        req.setEmail("x@test.com");
        req.setRole(Role.CLIENT);

        StepVerifier.create(service.inviteMember(ORG_ID, "owner@test.com", req))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("client role"))
                .verify();
    }

    @Test
    void inviteMember_alreadyMember_returnsError() {
        User requester = user(1L, "owner@test.com", Role.CLIENT);
        when(userRepo.findByUsername("owner@test.com")).thenReturn(Mono.just(requester));

        OrganisationMember ownerMember = member(ORG_ID, 1L, Role.CLIENT);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 1L)).thenReturn(Mono.just(ownerMember));

        ClientProfile cp = clientProfile(CP_ID, "owner@test.com");
        when(clientRepo.findByEmailAddress("owner@test.com")).thenReturn(Mono.just(cp));

        Organisation org = org(ORG_ID, CP_ID, "Acme");
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.just(org));

        User existing = user(2L, "existing@test.com", Role.AUDITOR);
        when(userRepo.findByUsername("existing@test.com")).thenReturn(Mono.just(existing));
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 2L))
                .thenReturn(Mono.just(member(ORG_ID, 2L, Role.AUDITOR)));

        InviteMemberRequest req = new InviteMemberRequest();
        req.setEmail("existing@test.com");
        req.setRole(Role.AUDITOR);

        StepVerifier.create(service.inviteMember(ORG_ID, "owner@test.com", req))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("already a member"))
                .verify();
    }

    // ──────────────────────────── removeMember ───────────────────────────────

    @Test
    void removeMember_ownerRemovesMember() {
        User owner = user(1L, "owner@test.com", Role.CLIENT);
        when(userRepo.findByUsername("owner@test.com")).thenReturn(Mono.just(owner));

        OrganisationMember ownerMember = member(ORG_ID, 1L, Role.CLIENT);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 1L)).thenReturn(Mono.just(ownerMember));

        OrganisationMember target = member(ORG_ID, 2L, Role.MEMBER);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 2L)).thenReturn(Mono.just(target));
        when(memberRepo.deleteByOrganisationIdAndUserId(ORG_ID, 2L)).thenReturn(Mono.empty());

        StepVerifier.create(service.removeMember(ORG_ID, "owner@test.com", 2L))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK)
                .verifyComplete();
    }

    @Test
    void removeMember_cannotRemoveOwner() {
        User owner = user(1L, "owner@test.com", Role.CLIENT);
        when(userRepo.findByUsername("owner@test.com")).thenReturn(Mono.just(owner));

        OrganisationMember ownerMember = member(ORG_ID, 1L, Role.CLIENT);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 1L)).thenReturn(Mono.just(ownerMember));

        OrganisationMember anotherOwner = member(ORG_ID, 2L, Role.CLIENT);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 2L)).thenReturn(Mono.just(anotherOwner));

        StepVerifier.create(service.removeMember(ORG_ID, "owner@test.com", 2L))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("owner"))
                .verify();
    }

    // ──────────────────────────── transferOwnership ──────────────────────────

    @Test
    void transferOwnership_swapsRoles() {
        User owner = user(1L, "owner@test.com", Role.CLIENT);
        when(userRepo.findByUsername("owner@test.com")).thenReturn(Mono.just(owner));

        OrganisationMember ownerMember = member(ORG_ID, 1L, Role.CLIENT);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 1L)).thenReturn(Mono.just(ownerMember));

        User newOwnerUser = user(2L, "newowner@test.com", Role.MEMBER);
        when(userRepo.findByUsername("newowner@test.com")).thenReturn(Mono.just(newOwnerUser));

        OrganisationMember newOwnerMember = member(ORG_ID, 2L, Role.MEMBER);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 2L)).thenReturn(Mono.just(newOwnerMember));

        UUID newOwnerCpId = UUID.randomUUID();
        ClientProfile newOwnerCp = clientProfile(newOwnerCpId, "newowner@test.com");
        when(clientRepo.findByEmailAddress("newowner@test.com")).thenReturn(Mono.just(newOwnerCp));

        when(memberRepo.save(any())).thenReturn(Mono.just(new OrganisationMember()));

        Organisation org = org(ORG_ID, CP_ID, "Acme");
        when(orgRepo.findById(ORG_ID)).thenReturn(Mono.just(org));
        when(orgRepo.save(any())).thenReturn(Mono.just(org));

        StepVerifier.create(service.transferOwnership(ORG_ID, "owner@test.com", "newowner@test.com"))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK && r.getMessage().contains("transferred"))
                .verifyComplete();
    }

    @Test
    void transferOwnership_nonMemberGetsError() {
        User owner = user(1L, "owner@test.com", Role.CLIENT);
        when(userRepo.findByUsername("owner@test.com")).thenReturn(Mono.just(owner));

        OrganisationMember ownerMember = member(ORG_ID, 1L, Role.CLIENT);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 1L)).thenReturn(Mono.just(ownerMember));

        User nonMember = user(99L, "nonmember@test.com", Role.MEMBER);
        when(userRepo.findByUsername("nonmember@test.com")).thenReturn(Mono.just(nonMember));
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 99L)).thenReturn(Mono.empty());

        StepVerifier.create(service.transferOwnership(ORG_ID, "owner@test.com", "nonmember@test.com"))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("already be a member"))
                .verify();
    }

    // ──────────────────────────── revokeInvitation ───────────────────────────

    @Test
    void revokeInvitation_pendingInvitation_revoked() {
        User owner = user(1L, "owner@test.com", Role.CLIENT);
        when(userRepo.findByUsername("owner@test.com")).thenReturn(Mono.just(owner));

        OrganisationMember ownerMember = member(ORG_ID, 1L, Role.CLIENT);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 1L)).thenReturn(Mono.just(ownerMember));

        OrganisationInvitation inv = invitation(INV_ID, ORG_ID, "x@test.com");
        inv.setStatus(InvitationStatus.PENDING);
        when(invitationRepo.findByIdAndOrganisationId(INV_ID, ORG_ID)).thenReturn(Mono.just(inv));
        when(invitationRepo.save(any())).thenReturn(Mono.just(inv));

        StepVerifier.create(service.revokeInvitation(ORG_ID, "owner@test.com", INV_ID))
                .expectNextMatches(r -> r.getStatus() == HttpStatus.OK && r.getMessage().contains("revoked"))
                .verifyComplete();
    }

    @Test
    void revokeInvitation_acceptedInvitation_returnsError() {
        User owner = user(1L, "owner@test.com", Role.CLIENT);
        when(userRepo.findByUsername("owner@test.com")).thenReturn(Mono.just(owner));

        OrganisationMember ownerMember = member(ORG_ID, 1L, Role.CLIENT);
        when(memberRepo.findByOrganisationIdAndUserId(ORG_ID, 1L)).thenReturn(Mono.just(ownerMember));

        OrganisationInvitation inv = invitation(INV_ID, ORG_ID, "x@test.com");
        inv.setStatus(InvitationStatus.ACCEPTED);
        when(invitationRepo.findByIdAndOrganisationId(INV_ID, ORG_ID)).thenReturn(Mono.just(inv));

        StepVerifier.create(service.revokeInvitation(ORG_ID, "owner@test.com", INV_ID))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("already been accepted"))
                .verify();
    }

    // ──────────────────────────── helpers ────────────────────────────────────

    private User user(Long id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setUsername(email);
        u.setFullName("Test User");
        u.setRole(role);
        u.setAuthProvider("JWT");
        u.setVerified(true);
        return u;
    }

    private ClientProfile clientProfile(UUID id, String email) {
        ClientProfile cp = new ClientProfile();
        cp.setId(id);
        cp.setEmailAddress(email);
        cp.setFirstName("Test");
        cp.setLastName("User");
        return cp;
    }

    private Organisation org(UUID id, UUID clientId, String name) {
        Organisation o = new Organisation();
        o.setId(id);
        o.setClientId(clientId);
        o.setName(name);
        o.setIndustry("Finance");
        o.setFiscalYearStart("01-01");
        o.setFiscalYearEnd("12-31");
        o.setDefaultCurrency("USD");
        o.setCreatedAt(LocalDateTime.now());
        return o;
    }

    private OrganisationMember member(UUID orgId, Long userId, Role role) {
        OrganisationMember m = new OrganisationMember();
        m.setOrganisationId(orgId);
        m.setUserId(userId);
        m.setRole(role);
        m.setStatus(MemberStatus.ACTIVE);
        m.setJoinedAt(LocalDateTime.now());
        return m;
    }

    private OrganisationCurrency currency(UUID orgId, String code) {
        OrganisationCurrency c = new OrganisationCurrency();
        c.setOrganisationId(orgId);
        c.setCurrencyCode(code);
        c.setPrimaryCurrency(true);
        return c;
    }

    private OrganisationInvitation invitation(UUID id, UUID orgId, String email) {
        OrganisationInvitation inv = new OrganisationInvitation();
        inv.setId(id);
        inv.setOrganisationId(orgId);
        inv.setInvitedEmail(email);
        inv.setRole(Role.MEMBER);
        inv.setToken(UUID.randomUUID().toString());
        inv.setStatus(InvitationStatus.PENDING);
        inv.setInvitedBy(UUID.randomUUID());
        inv.setExpiresAt(LocalDateTime.now().plusHours(72));
        inv.setCreatedAt(LocalDateTime.now());
        return inv;
    }
}