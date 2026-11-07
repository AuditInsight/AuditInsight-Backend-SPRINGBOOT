package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminFreeAccessResponse;
import com.diana.auditinsightbackendspringboot.DTOs.EmergencyOverrideDependent;
import com.diana.auditinsightbackendspringboot.Enum.AccessBasis;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.PlatformAccessSettings;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import com.diana.auditinsightbackendspringboot.Repositories.PlatformAccessSettingsRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import com.diana.auditinsightbackendspringboot.Services.EntitlementService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * The platform-wide emergency override: every organisation can use the app for a short, stated period during an
 * incident (for example when payments are down). It is not a trial or a promotion — those are trial grants and
 * campaigns. It ranks below paid time and trials, needs a reason, and lasts at most {@link #MAX_DAYS} days.
 */
@Service
public class AdminFreeAccessService {

    public static final int MAX_DAYS = 30;

    private final PlatformAccessSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final AdminAuditService adminAuditService;
    private final OrganisationRepository organisationRepository;
    private final EntitlementService entitlementService;

    public AdminFreeAccessService(PlatformAccessSettingsRepository settingsRepository,
                                  UserRepository userRepository,
                                  AdminAuditService adminAuditService,
                                  OrganisationRepository organisationRepository,
                                  EntitlementService entitlementService) {
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.adminAuditService = adminAuditService;
        this.organisationRepository = organisationRepository;
        this.entitlementService = entitlementService;
    }

    public Mono<AdminFreeAccessResponse> getStatus() {
        return settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)
                .map(this::toResponse)
                .defaultIfEmpty(new AdminFreeAccessResponse(false, null, null, null, null));
    }

    public Mono<AdminFreeAccessResponse> start(int durationDays, String reason, String actingAdminUsername) {
        if (durationDays < 1 || durationDays > MAX_DAYS) {
            return Mono.error(new InvalidRecord("The emergency override must last between 1 and " + MAX_DAYS + " days"));
        }
        if (reason == null || reason.isBlank()) {
            return Mono.error(new InvalidRecord("A reason is required for the emergency override"));
        }
        return resolveAdmin(actingAdminUsername)
                .flatMap(admin -> settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)
                        .defaultIfEmpty(newSettings())
                        .flatMap(settings -> {
                            AdminFreeAccessResponse before = toResponse(settings);
                            LocalDateTime now = LocalDateTime.now();
                            settings.setFreeAccessStartedAt(now);
                            settings.setFreeAccessEndsAt(now.plusDays(durationDays));
                            settings.setReason(reason.trim());
                            settings.setUpdatedAt(now);
                            settings.setUpdatedBy(admin.getId());
                            return settingsRepository.save(settings)
                                    .flatMap(saved -> {
                                        AdminFreeAccessResponse after = toResponse(saved);
                                        return adminAuditService.record(admin.getId(), "START_PLATFORM_FREE_ACCESS",
                                                        "PLATFORM_ACCESS", PlatformAccessSettings.GLOBAL_ID, before, after,
                                                        reason)
                                                .thenReturn(after);
                                    });
                        }));
    }

    public Mono<AdminFreeAccessResponse> end(String reason, String actingAdminUsername) {
        return resolveAdmin(actingAdminUsername)
                .flatMap(admin -> settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)
                        .switchIfEmpty(Mono.error(new InvalidRecord("The emergency override is not active")))
                        .flatMap(settings -> {
                            AdminFreeAccessResponse before = toResponse(settings);
                            if (!before.enabled()) {
                                return Mono.error(new InvalidRecord("The emergency override is not active"));
                            }

                            LocalDateTime now = LocalDateTime.now();
                            settings.setFreeAccessEndsAt(now);
                            settings.setUpdatedAt(now);
                            settings.setUpdatedBy(admin.getId());
                            return settingsRepository.save(settings)
                                    .flatMap(saved -> {
                                        AdminFreeAccessResponse after = toResponse(saved);
                                        return adminAuditService.record(admin.getId(), "END_PLATFORM_FREE_ACCESS",
                                                        "PLATFORM_ACCESS", PlatformAccessSettings.GLOBAL_ID, before, after,
                                                        reason)
                                                .thenReturn(after);
                                    });
                        }));
    }

    /** Organisations that would lose access if the override ended now (no paid time or trial running). */
    public Flux<EmergencyOverrideDependent> dependents() {
        return organisationRepository.findAll()
                .concatMap(org -> entitlementService.resolve(org.getId())
                        .filter(e -> e.basis() == AccessBasis.EMERGENCY_OVERRIDE)
                        .map(e -> new EmergencyOverrideDependent(org.getId(), org.getName(),
                                e.nextPaid() == null ? null : e.nextPaid().getStartDate())));
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
        return new AdminFreeAccessResponse(enabled, settings.getFreeAccessStartedAt(), endsAt, settings.getUpdatedAt(),
                settings.getReason());
    }
}
