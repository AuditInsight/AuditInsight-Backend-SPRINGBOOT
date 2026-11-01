package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.MemberStatus;
import com.diana.auditinsightbackendspringboot.Enum.Permission;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.OrganisationMember;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationMemberRepository;
import com.diana.auditinsightbackendspringboot.Repositories.SubscriptionRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Single shared place to resolve "is this user an active member of this organisation, and (for
 * gated operations) is the organisation's paid subscription still current" — replaces what used
 * to be three independent copies of the same membership-resolution logic in TransactionService,
 * ReviewQueueService and EvidenceService.
 */
@Service
public class OrgAccessService {

    private static final Map<Role, Set<Permission>> ROLE_PERMISSIONS = new EnumMap<>(Role.class);

    static {
        Set<Permission> memberLevel = Set.of(
                Permission.TRANSACTION_WRITE, Permission.TRANSACTION_READ,
                Permission.EVIDENCE_UPLOAD, Permission.EVIDENCE_VIEW,
                Permission.REVIEW_RESOLVE);
        Set<Permission> clientLevel = EnumSet.copyOf(memberLevel);
        clientLevel.add(Permission.ORG_MANAGE);

        ROLE_PERMISSIONS.put(Role.CLIENT, clientLevel);
        ROLE_PERMISSIONS.put(Role.MEMBER, memberLevel);
        ROLE_PERMISSIONS.put(Role.ADMIN, memberLevel);
        ROLE_PERMISSIONS.put(Role.AUDITOR, Set.of(
                Permission.TRANSACTION_READ, Permission.EVIDENCE_VIEW, Permission.REVIEW_FLAG));
    }

    private final UserRepository userRepo;
    private final OrganisationMemberRepository memberRepo;
    private final SubscriptionRepository subscriptionRepo;

    public OrgAccessService(UserRepository userRepo, OrganisationMemberRepository memberRepo,
                             SubscriptionRepository subscriptionRepo) {
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
        this.subscriptionRepo = subscriptionRepo;
    }

    public record OrgMemberContext(User user, OrganisationMember member) {
        public Role role() {
            return member.getRole();
        }
    }

    public boolean hasPermission(Role role, Permission permission) {
        return ROLE_PERMISSIONS.getOrDefault(role, Set.of()).contains(permission);
    }

    /** Active org membership only — no subscription-status check. */
    public Mono<OrgMemberContext> resolveActiveMember(UUID organisationId, String email) {
        return userRepo.findByUsername(email)
                .switchIfEmpty(Mono.error(new InvalidRecord("User not found")))
                .flatMap(user -> memberRepo.findByOrganisationIdAndUserId(organisationId, user.getId())
                        .switchIfEmpty(Mono.error(new ForbiddenException(
                                "You are not a member of this organisation")))
                        .flatMap(member -> member.getStatus() != MemberStatus.ACTIVE
                                ? Mono.error(new ForbiddenException("Your membership is not active"))
                                : Mono.just(new OrgMemberContext(user, member))));
    }

    /** Active org membership, gated on the organisation having a current paid subscription. */
    public Mono<OrgMemberContext> resolveGatedMember(UUID organisationId, String email) {
        return resolveActiveMember(organisationId, email)
                .flatMap(ctx -> assertSubscriptionCurrent(organisationId).thenReturn(ctx));
    }

    private Mono<Void> assertSubscriptionCurrent(UUID organisationId) {
        return subscriptionRepo.findFirstByOrganisationIdOrderByCreatedAtDesc(organisationId)
                .switchIfEmpty(Mono.error(new ForbiddenException(
                        "A paid subscription is required to use this feature. Please complete checkout to continue.")))
                .flatMap(this::assertNotExpired);
    }

    private Mono<Void> assertNotExpired(Subscription subscription) {
        boolean active = subscription.getStatus() == SubscriptionStatus.ACTIVE
                && subscription.getEndDate() != null
                && subscription.getEndDate().isAfter(LocalDateTime.now());
        return active ? Mono.empty() : Mono.error(new ForbiddenException(
                "A paid subscription is required to use this feature. Please complete checkout to continue."));
    }
}
