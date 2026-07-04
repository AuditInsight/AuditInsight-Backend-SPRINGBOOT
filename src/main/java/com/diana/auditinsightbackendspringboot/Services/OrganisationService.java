package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Enum.InvitationStatus;
import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
import com.diana.auditinsightbackendspringboot.Enum.OrganisationType;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.*;
import com.diana.auditinsightbackendspringboot.Repositories.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class OrganisationService {

    private final OrganisationRepository orgRepo;
    private final OrganisationMemberRepository memberRepo;
    private final OrganisationCurrencyRepository currencyRepo;
    private final OrganisationInvitationRepository invitationRepo;
    private final UserRepository userRepo;
    private final ClientRepository clientRepo;
    private final EmailService emailService;
    private final PasswordEncoder encoder;

    public OrganisationService(
            OrganisationRepository orgRepo,
            OrganisationMemberRepository memberRepo,
            OrganisationCurrencyRepository currencyRepo,
            OrganisationInvitationRepository invitationRepo,
            UserRepository userRepo,
            ClientRepository clientRepo,
            EmailService emailService,
            PasswordEncoder encoder) {
        this.orgRepo = orgRepo;
        this.memberRepo = memberRepo;
        this.currencyRepo = currencyRepo;
        this.invitationRepo = invitationRepo;
        this.userRepo = userRepo;
        this.clientRepo = clientRepo;
        this.emailService = emailService;
        this.encoder = encoder;
    }



    private Mono<User> getUser(String email) {
        return userRepo.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("User not found")));
    }


    private Mono<OrganisationMember> assertIsOwner(UUID orgId, Long userId) {
        return memberRepo.findByOrganisationIdAndUserId(orgId, userId)
                .switchIfEmpty(Mono.error(new InvalidRecord("You are not a member of this organisation")))
                .flatMap(m -> {
                    if (m.getStatus() != MemberStatus.ACTIVE) {
                        return Mono.error(new InvalidRecord(
                                "Your membership is pending. Please activate it via your invitation link first."));
                    }
                    return m.getRole() == Role.CLIENT
                            ? Mono.just(m)
                            : Mono.error(new InvalidRecord("Only the owner can perform this action"));
                });
    }

    /** Only ACTIVE members can access organisation resources. */
    private Mono<Void> assertIsMember(UUID orgId, Long userId) {
        return memberRepo.findByOrganisationIdAndUserId(orgId, userId)
                .switchIfEmpty(Mono.error(new InvalidRecord("You are not a member of this organisation")))
                .flatMap(m -> m.getStatus() == MemberStatus.ACTIVE
                        ? Mono.empty()
                        : Mono.error(new InvalidRecord(
                                "Your membership is pending. Please activate it via your invitation link first.")))
                .then();
    }

    private Mono<OrganisationResponse> buildResponse(String message, Organisation org) {
        return currencyRepo.findAllByOrganisationId(org.getId())
                .map(OrganisationCurrency::getCurrencyCode)
                .collectList()
                .map(codes -> {
                    OrganisationResponse r = new OrganisationResponse();
                    r.setMessage(message);
                    r.setOrganisationId(org.getId());
                    r.setName(org.getName());
                    r.setIndustry(org.getIndustry().name());
                    r.setFiscalYearStart(org.getFiscalYearStart());
                    r.setFiscalYearEnd(org.getFiscalYearEnd());
                    r.setDefaultCurrency(org.getDefaultCurrency());
                    r.setCurrencies(codes);
                    r.setCreatedAt(org.getCreatedAt());
                    return r;
                });
    }

    private List<String> normaliseCurrencies(List<String> raw) {
        return raw.stream().map(String::toUpperCase).distinct().toList();
    }

    private String generateDefaultPassword() {
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String digits = "0123456789";
        String special = "@$!%*?&";
        String all = lower + upper + digits + special;

        SecureRandom rng = new SecureRandom();
        List<Character> chars = new ArrayList<>();
        chars.add(lower.charAt(rng.nextInt(lower.length())));
        chars.add(upper.charAt(rng.nextInt(upper.length())));
        chars.add(digits.charAt(rng.nextInt(digits.length())));
        chars.add(special.charAt(rng.nextInt(special.length())));
        for (int i = 4; i < 10; i++) {
            chars.add(all.charAt(rng.nextInt(all.length())));
        }
        Collections.shuffle(chars, rng);
        StringBuilder sb = new StringBuilder();
        for (char c : chars) sb.append(c);
        return sb.toString();
    }


    public Mono<OrganisationResponse> createOrganisation(
            String email,
            CreateOrganisationRequest request) {
        return getUser(email)
                .flatMap(user -> {
                    if (user.getRole() != Role.CLIENT) {
                        return Mono.error(new InvalidRecord("Only CLIENT users can create organisations"));
                    }
                    return clientRepo.findByEmailAddress(email)
                            .switchIfEmpty(Mono.error(new InvalidRecord(
                                    "Client profile not found. Please complete your profile first.")));
                })
                .flatMap(clientProfile ->
                        userRepo.findByUsername(email).flatMap(user -> {
                            List<String> currencies = normaliseCurrencies(request.getCurrencies());

                            Organisation org = new Organisation();
                            org.setClientId(clientProfile.getId());
                            org.setName(request.getName());
                            org.setIndustry(request.getIndustry());
                            org.setSize(request.getSize());
                            org.setFiscalYearStart(request.getFiscalYearStart());
                            org.setFiscalYearEnd(request.getFiscalYearEnd());
                            org.setDefaultCurrency(currencies.get(0));
                            org.setCreatedAt(LocalDateTime.now());

                            return orgRepo.save(org).flatMap(saved -> {
                                // The creator is always ACTIVE as CLIENT from day one.
                                OrganisationMember ownerMember = new OrganisationMember();
                                ownerMember.setOrganisationId(saved.getId());
                                ownerMember.setUserId(user.getId());


                                ownerMember.setRole(Role.CLIENT);
                                ownerMember.setStatus(MemberStatus.ACTIVE);
                                ownerMember.setJoinedAt(LocalDateTime.now());

                                Flux<OrganisationCurrency> saveCurrencies = Flux.range(0, currencies.size())
                                        .map(i -> {
                                            OrganisationCurrency c = new OrganisationCurrency();
                                            c.setOrganisationId(saved.getId());
                                            c.setCurrencyCode(currencies.get(i));
                                            c.setPrimaryCurrency(i == 0);
                                            return c;
                                        })
                                        .flatMap(currencyRepo::save);

                                return memberRepo.save(ownerMember)
                                        .thenMany(saveCurrencies)
                                        .then(buildResponse("Organisation created successfully", saved));
                            });
                        })
                );
    }


    public Flux<Organisation> listMyOrganisations(String email) {
        return getUser(email)
                .flatMapMany(user -> memberRepo.findAllByUserId(user.getId()))
                .flatMap(member -> orgRepo.findById(member.getOrganisationId()));
    }


    public Mono<OrganisationResponse> getOrganisation(UUID orgId, String email) {
        return getUser(email)
                .flatMap(user -> assertIsMember(orgId, user.getId()))
                .then(orgRepo.findById(orgId))
                .switchIfEmpty(Mono.error(new InvalidRecord("Organisation not found")))
                .flatMap(org -> buildResponse(null, org));
    }


    public Mono<OrganisationResponse> updateOrganisation(
            UUID orgId,
            String email,
            UpdateOrganisationRequest request) {
        return getUser(email)
                .flatMap(user -> assertIsOwner(orgId, user.getId()))
                .then(orgRepo.findById(orgId))
                .switchIfEmpty(Mono.error(new InvalidRecord("Organisation not found")))
                .flatMap(org -> {
                    if (request.getName() != null) org.setName(request.getName());
                    if (request.getIndustry() != null) org.setIndustry(request.getIndustry());
                    if (request.getFiscalYearStart() != null) org.setFiscalYearStart(request.getFiscalYearStart());
                    if (request.getFiscalYearEnd() != null) org.setFiscalYearEnd(request.getFiscalYearEnd());

                    if (request.getCurrencies() != null && !request.getCurrencies().isEmpty()) {
                        List<String> currencies = normaliseCurrencies(request.getCurrencies());
                        org.setDefaultCurrency(currencies.get(0));

                        Flux<OrganisationCurrency> replaceCurrencies = Flux.range(0, currencies.size())
                                .map(i -> {
                                    OrganisationCurrency c = new OrganisationCurrency();
                                    c.setOrganisationId(org.getId());
                                    c.setCurrencyCode(currencies.get(i));
                                    c.setPrimaryCurrency(i == 0);
                                    return c;
                                })
                                .flatMap(currencyRepo::save);

                        return currencyRepo.deleteAllByOrganisationId(org.getId())
                                .thenMany(replaceCurrencies)
                                .then(orgRepo.save(org))
                                .flatMap(saved -> buildResponse("Organisation updated successfully", saved));
                    }

                    return orgRepo.save(org)
                            .flatMap(saved -> buildResponse("Organisation updated successfully", saved));
                });
    }


    public Mono<ResponseMessage> inviteMember(
            UUID orgId,
            String requesterEmail,
            InviteMemberRequest request) {
        if (request.getRole() == Role.CLIENT) {
            return Mono.error(new InvalidRecord("Cannot assign client role via invitation"));
        }

        return getUser(requesterEmail)
                .flatMap(requester -> assertIsOwner(orgId, requester.getId())
                        .then(clientRepo.findByEmailAddress(requesterEmail))
                        .switchIfEmpty(Mono.error(new InvalidRecord("Requester client profile not found")))
                        .flatMap(requesterProfile ->
                                orgRepo.findById(orgId)
                                        .switchIfEmpty(Mono.error(new InvalidRecord("Organisation not found")))
                                        .flatMap(org ->
                                                userRepo.findByUsername(request.getEmail())
                                                        // PATH A: existing account — PENDING until token-validated login
                                                        .flatMap(existingUser ->
                                                                memberRepo.findByOrganisationIdAndUserId(orgId, existingUser.getId())
                                                                        .flatMap(existing -> Mono.<ResponseMessage>error(
                                                                                new InvalidRecord("User is already a member of this organisation")))
                                                                        .switchIfEmpty(Mono.defer(() -> {
                                                                            String token = UUID.randomUUID().toString();

                                                                            OrganisationMember member = new OrganisationMember();
                                                                            member.setOrganisationId(orgId);
                                                                            member.setUserId(existingUser.getId());
                                                                            member.setRole(request.getRole());
                                                                            member.setStatus(MemberStatus.PENDING);
                                                                            member.setJoinedAt(LocalDateTime.now());

                                                                            OrganisationInvitation inv = pendingInvitation(
                                                                                    orgId, request.getEmail(),
                                                                                    request.getRole(), token,
                                                                                    requesterProfile.getId());

                                                                            return memberRepo.save(member)
                                                                                    .then(invitationRepo.save(inv))
                                                                                    .then(Mono.fromRunnable(() ->
                                                                                            emailService.sendExistingUserInvitationEmail(
                                                                                                    existingUser.getUsername(),
                                                                                                    existingUser.getFullName(),
                                                                                                    org.getName(),
                                                                                                    request.getRole().name(),
                                                                                                    token))
                                                                                            .subscribeOn(Schedulers.boundedElastic()))
                                                                                    .thenReturn(new ResponseMessage(HttpStatus.OK,
                                                                                            "Invitation sent successfully"));
                                                                        }))
                                                        )
                                                        // PATH B: new user — create account + send credentials with token
                                                        .switchIfEmpty(Mono.defer(() -> {
                                                            String defaultPassword = generateDefaultPassword();
                                                            String token = UUID.randomUUID().toString();

                                                            User newUser = new User();
                                                            newUser.setUsername(request.getEmail());
                                                            newUser.setPassword(encoder.encode(defaultPassword));
                                                            newUser.setFullName(request.getEmail());
                                                            newUser.setRole(request.getRole());
                                                            newUser.setAuthProvider("JWT");
                                                            newUser.setVerified(true);
                                                            newUser.setMustChangePassword(true);
                                                            newUser.setCreatedAt(LocalDateTime.now());

                                                            return userRepo.save(newUser)
                                                                    .flatMap(savedUser -> {
                                                                        OrganisationMember member = new OrganisationMember();
                                                                        member.setOrganisationId(orgId);
                                                                        member.setUserId(savedUser.getId());
                                                                        member.setRole(request.getRole());
                                                                        member.setStatus(MemberStatus.PENDING);
                                                                        member.setJoinedAt(LocalDateTime.now());

                                                                        OrganisationInvitation inv = pendingInvitation(
                                                                                orgId, request.getEmail(),
                                                                                request.getRole(), token,
                                                                                requesterProfile.getId());

                                                                        return memberRepo.save(member)
                                                                                .then(invitationRepo.save(inv))
                                                                                .then(Mono.fromRunnable(() ->
                                                                                        emailService.sendMemberCredentialsEmail(
                                                                                                request.getEmail(),
                                                                                                defaultPassword,
                                                                                                org.getName(),
                                                                                                request.getRole().name(),
                                                                                                token))
                                                                                        .subscribeOn(Schedulers.boundedElastic()))
                                                                                .thenReturn(new ResponseMessage(HttpStatus.OK,
                                                                                        "Account created and invitation sent successfully"));
                                                                    });
                                                        }))
                                        )
                        )
                );
    }

    private OrganisationInvitation pendingInvitation(
            UUID orgId,
            String email,
            Role role,
            String token,
            UUID invitedBy) {
        OrganisationInvitation inv = new OrganisationInvitation();
        inv.setOrganisationId(orgId);
        inv.setInvitedEmail(email);
        inv.setRole(role);
        inv.setToken(token);
        inv.setStatus(InvitationStatus.PENDING);
        inv.setInvitedBy(invitedBy);
        inv.setExpiresAt(LocalDateTime.now().plusHours(72));
        inv.setCreatedAt(LocalDateTime.now());
        return inv;
    }


    public Flux<OrganisationMemberResponse> listMembers(UUID orgId, String email) {
        return getUser(email)
                .flatMap(user -> assertIsMember(orgId, user.getId()))
                .thenMany(memberRepo.findAllByOrganisationId(orgId))
                .flatMap(member -> userRepo.findById(member.getUserId())
                        .map(u -> new OrganisationMemberResponse(
                                member.getUserId(),
                                u.getUsername(),
                                member.getRole(),
                                member.getStatus(),
                                member.getJoinedAt())));
    }


    public Mono<ResponseMessage> removeMember(UUID orgId, String requesterEmail, Long userId) {
        return getUser(requesterEmail)
                .flatMap(requester -> assertIsOwner(orgId, requester.getId()))
                .then(memberRepo.findByOrganisationIdAndUserId(orgId, userId))
                .switchIfEmpty(Mono.error(new InvalidRecord("Member not found")))
                .flatMap(member -> {
                    if (member.getRole() == Role.CLIENT) {
                        return Mono.error(new InvalidRecord("Cannot remove the organisation owner"));
                    }
                    return memberRepo.deleteByOrganisationIdAndUserId(orgId, userId)
                            .thenReturn(new ResponseMessage(HttpStatus.OK, "Member removed successfully"));
                });
    }


    public Mono<ResponseMessage> transferOwnership(UUID orgId, String requesterEmail, String newOwnerEmail) {
        return getUser(requesterEmail).flatMap(requester ->
                assertIsOwner(orgId, requester.getId()).flatMap(currentOwnerMember ->
                        userRepo.findByUsername(newOwnerEmail)
                                .switchIfEmpty(Mono.error(new InvalidRecord("New owner user not found")))
                                .flatMap(newOwnerUser ->
                                        memberRepo.findByOrganisationIdAndUserId(orgId, newOwnerUser.getId())
                                                .switchIfEmpty(Mono.error(new InvalidRecord(
                                                        "New owner must already be a member of this organisation")))
                                                .flatMap(newOwnerMember ->
                                                        orgRepo.findById(orgId)
                                                                .switchIfEmpty(Mono.error(new InvalidRecord("Organisation not found")))
                                                                .flatMap(org -> {
                                                                    newOwnerMember.setRole(Role.CLIENT);
                                                                    currentOwnerMember.setRole(Role.MEMBER);

                                                                    // Update org.client_id if new owner has a client profile, otherwise keep existing
                                                                    Mono<Void> updateClientId = clientRepo.findByEmailAddress(newOwnerEmail)
                                                                            .flatMap(profile -> {
                                                                                org.setClientId(profile.getId());
                                                                                return orgRepo.save(org);
                                                                            })
                                                                            .switchIfEmpty(orgRepo.save(org))
                                                                            .then();

                                                                    return memberRepo.save(newOwnerMember)
                                                                            .then(memberRepo.save(currentOwnerMember))
                                                                            .then(updateClientId)
                                                                            .then(Mono.fromRunnable(() -> {
                                                                                emailService.sendOwnershipTransferEmail(
                                                                                        newOwnerEmail,
                                                                                        newOwnerUser.getFullName(),
                                                                                        org.getName(),
                                                                                        "CLIENT");
                                                                                emailService.sendOwnershipTransferEmail(
                                                                                        requesterEmail,
                                                                                        requester.getFullName(),
                                                                                        org.getName(),
                                                                                        "MEMBER");
                                                                            }).subscribeOn(Schedulers.boundedElastic()))
                                                                            .thenReturn(new ResponseMessage(HttpStatus.OK,
                                                                                    "Ownership transferred successfully"));
                                                                })
                                                )
                                )
                )
        );
    }


    public Flux<OrganisationInvitationResponse> listPendingInvitations(UUID orgId, String email) {
        return getUser(email)
                .flatMap(user -> assertIsOwner(orgId, user.getId()))
                .thenMany(invitationRepo.findAllByOrganisationIdAndStatus(orgId, InvitationStatus.PENDING))
                .map(inv -> new OrganisationInvitationResponse(
                        inv.getInvitedEmail(),
                        inv.getRole(),
                        inv.getStatus(),
                        inv.getExpiresAt(),
                        inv.getCreatedAt()));
    }


    public Mono<ResponseMessage> revokeInvitation(UUID orgId, String email, UUID invitationId) {
        return getUser(email)
                .flatMap(user -> assertIsOwner(orgId, user.getId()))
                .then(invitationRepo.findByIdAndOrganisationId(invitationId, orgId))
                .switchIfEmpty(Mono.error(new InvalidRecord("Invitation not found")))
                .flatMap(inv -> switch (inv.getStatus()) {
                    case ACCEPTED -> Mono.error(new InvalidRecord("This invitation has already been accepted"));
                    case EXPIRED  -> Mono.error(new InvalidRecord("This invitation has expired. Please request a new one."));
                    case REVOKED  -> Mono.error(new InvalidRecord("This invitation has been revoked"));
                    default -> {
                        inv.setStatus(InvitationStatus.REVOKED);
                        yield invitationRepo.save(inv)
                                .thenReturn(new ResponseMessage(HttpStatus.OK, "Invitation revoked successfully"));
                    }
                });
    }
}
