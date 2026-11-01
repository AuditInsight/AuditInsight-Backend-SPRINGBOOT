package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminFreeAccessResponse;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.PlatformAccessSettings;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.PlatformAccessSettingsRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class AdminFreeAccessService {

    private final PlatformAccessSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final AdminAuditService adminAuditService;

    public AdminFreeAccessService(PlatformAccessSettingsRepository settingsRepository,
                                  UserRepository userRepository,
                                  AdminAuditService adminAuditService) {
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.adminAuditService = adminAuditService;
    }

    public Mono<AdminFreeAccessResponse> getStatus() {
        return settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)
                .map(this::toResponse)
                .defaultIfEmpty(new AdminFreeAccessResponse(false, null, null, null));
    }

    public Mono<AdminFreeAccessResponse> start(int durationDays, String actingAdminUsername) {
        return resolveAdmin(actingAdminUsername)
                .flatMap(admin -> settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)
                        .defaultIfEmpty(newSettings())
                        .flatMap(settings -> {
                            AdminFreeAccessResponse before = toResponse(settings);
                            LocalDateTime now = LocalDateTime.now();
                            settings.setFreeAccessStartedAt(now);
                            settings.setFreeAccessEndsAt(now.plusDays(durationDays));
                            settings.setUpdatedAt(now);
                            settings.setUpdatedBy(admin.getId());
                            return settingsRepository.save(settings)
                                    .flatMap(saved -> {
                                        AdminFreeAccessResponse after = toResponse(saved);
                                        return adminAuditService.record(admin.getId(), "START_PLATFORM_FREE_ACCESS",
                                                        "PLATFORM_ACCESS", PlatformAccessSettings.GLOBAL_ID, before, after)
                                                .thenReturn(after);
                                    });
                        }));
    }

    public Mono<AdminFreeAccessResponse> end(String actingAdminUsername) {
        return resolveAdmin(actingAdminUsername)
                .flatMap(admin -> settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)
                        .switchIfEmpty(Mono.error(new InvalidRecord("Platform free access is not active")))
                        .flatMap(settings -> {
                            AdminFreeAccessResponse before = toResponse(settings);
                            if (!before.enabled()) {
                                return Mono.error(new InvalidRecord("Platform free access is not active"));
                            }

                            LocalDateTime now = LocalDateTime.now();
                            settings.setFreeAccessEndsAt(now);
                            settings.setUpdatedAt(now);
                            settings.setUpdatedBy(admin.getId());
                            return settingsRepository.save(settings)
                                    .flatMap(saved -> {
                                        AdminFreeAccessResponse after = toResponse(saved);
                                        return adminAuditService.record(admin.getId(), "END_PLATFORM_FREE_ACCESS",
                                                        "PLATFORM_ACCESS", PlatformAccessSettings.GLOBAL_ID, before, after)
                                                .thenReturn(after);
                                    });
                        }));
    }

    private Mono<User> resolveAdmin(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new InvalidRecord("Admin user not found")));
    }

    private PlatformAccessSettings newSettings() {
        PlatformAccessSettings settings = new PlatformAccessSettings();
        settings.setId(PlatformAccessSettings.GLOBAL_ID);
        return settings;
    }

    private AdminFreeAccessResponse toResponse(PlatformAccessSettings settings) {
        LocalDateTime endsAt = settings.getFreeAccessEndsAt();
        boolean enabled = endsAt != null && endsAt.isAfter(LocalDateTime.now());
        return new AdminFreeAccessResponse(enabled, settings.getFreeAccessStartedAt(), endsAt, settings.getUpdatedAt());
    }
}
