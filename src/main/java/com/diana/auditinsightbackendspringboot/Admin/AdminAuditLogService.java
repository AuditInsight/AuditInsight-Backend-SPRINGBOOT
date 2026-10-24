package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminAuditLogResponse;
import com.diana.auditinsightbackendspringboot.DTOs.PageResult;
import com.diana.auditinsightbackendspringboot.Models.AdminAuditLog;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.AdminAuditLogRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AdminAuditLogService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final UserRepository userRepository;

    public AdminAuditLogService(AdminAuditLogRepository adminAuditLogRepository, UserRepository userRepository) {
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.userRepository = userRepository;
    }

    public Mono<PageResult<AdminAuditLogResponse>> listAuditLog(int page) {
        int safePage = Math.max(page, 1);
        long offset = (long) (safePage - 1) * DEFAULT_PAGE_SIZE;

        return adminAuditLogRepository.findPage(DEFAULT_PAGE_SIZE, offset)
                .flatMap(this::toResponse)
                .collectList()
                .zipWith(adminAuditLogRepository.countAll())
                .map(tuple -> PageResult.of(tuple.getT1(), tuple.getT2(), safePage, DEFAULT_PAGE_SIZE));
    }

    private Mono<AdminAuditLogResponse> toResponse(AdminAuditLog entry) {
        return userRepository.findById(entry.getAdminUserId())
                .map(User::getUsername)
                .defaultIfEmpty("Unknown admin")
                .map(username -> new AdminAuditLogResponse(entry.getId(), username, entry.getAction(),
                        entry.getTargetType(), entry.getTargetId(), entry.getBeforeState(), entry.getAfterState(),
                        entry.getCreatedAt()));
    }
}
