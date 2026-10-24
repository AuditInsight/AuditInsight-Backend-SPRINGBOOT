package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminUserOrganisationSummary;
import com.diana.auditinsightbackendspringboot.DTOs.AdminUserResponse;
import com.diana.auditinsightbackendspringboot.DTOs.PageResult;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Models.OrganisationMember;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationMemberRepository;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUserService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final UserRepository userRepository;
    private final OrganisationMemberRepository organisationMemberRepository;
    private final OrganisationRepository organisationRepository;

    public AdminUserService(UserRepository userRepository,
                             OrganisationMemberRepository organisationMemberRepository,
                             OrganisationRepository organisationRepository) {
        this.userRepository = userRepository;
        this.organisationMemberRepository = organisationMemberRepository;
        this.organisationRepository = organisationRepository;
    }

    public Mono<PageResult<AdminUserResponse>> listUsers(int page, Role role, String search) {
        int safePage = Math.max(page, 1);
        long offset = (long) (safePage - 1) * DEFAULT_PAGE_SIZE;
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        return userRepository.findPage(role, normalizedSearch, DEFAULT_PAGE_SIZE, offset)
                .collectList()
                .flatMap(this::attachOrganisations)
                .zipWith(userRepository.countPage(role, normalizedSearch))
                .map(tuple -> PageResult.of(tuple.getT1(), tuple.getT2(), safePage, DEFAULT_PAGE_SIZE));
    }

    private Mono<List<AdminUserResponse>> attachOrganisations(List<User> users) {
        List<Long> userIds = users.stream().map(User::getId).toList();
        if (userIds.isEmpty()) {
            return Mono.just(List.of());
        }
        return organisationMemberRepository.findAllByUserIdIn(userIds)
                .collectList()
                .flatMap(members -> {
                    List<UUID> orgIds = members.stream().map(OrganisationMember::getOrganisationId).distinct().toList();
                    return organisationRepository.findAllById(orgIds)
                            .collectMap(Organisation::getId, Organisation::getName)
                            .map(orgNames -> users.stream().map(user -> toResponse(user, members, orgNames)).toList());
                });
    }

    private AdminUserResponse toResponse(User user, List<OrganisationMember> allMembers, Map<UUID, String> orgNames) {
        List<AdminUserOrganisationSummary> organisations = allMembers.stream()
                .filter(member -> member.getUserId().equals(user.getId()))
                .map(member -> new AdminUserOrganisationSummary(
                        member.getOrganisationId(), orgNames.get(member.getOrganisationId()),
                        member.getRole(), member.getStatus()))
                .toList();
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                user.isActive(), user.isVerified(), user.getAuthProvider(), user.getCreatedAt(), organisations);
    }
}
