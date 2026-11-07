package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.AdminAuditLog;
import com.diana.auditinsightbackendspringboot.Models.PlatformAccessSettings;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.PlatformAccessSettingsRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminFreeAccessServiceTest {

    private static final String ADMIN_USERNAME = "info@auditinsight.tech";

    @Mock private PlatformAccessSettingsRepository settingsRepository;
    @Mock private UserRepository userRepository;
    @Mock private AdminAuditService adminAuditService;
    @Mock private com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository organisationRepository;
    @Mock private com.diana.auditinsightbackendspringboot.Services.EntitlementService entitlementService;

    private AdminFreeAccessService service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminFreeAccessService(settingsRepository, userRepository, adminAuditService,
                organisationRepository, entitlementService);
        admin = new User();
        admin.setId(42L);
        admin.setUsername(ADMIN_USERNAME);
        lenient().when(userRepository.findByUsername(ADMIN_USERNAME)).thenReturn(Mono.just(admin));
        lenient().when(adminAuditService.record(eq(42L), anyString(), eq("PLATFORM_ACCESS"),
                eq(PlatformAccessSettings.GLOBAL_ID), any(), any(), any()))
                .thenReturn(Mono.just(new AdminAuditLog()));
    }

    @Test
    void start_enablesFreeAccessForRequestedDurationAndAuditsChange() {
        when(settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)).thenReturn(Mono.empty());
        when(settingsRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.start(14, "Payments outage", ADMIN_USERNAME))
                .expectNextMatches(status -> status.enabled()
                        && status.startsAt() != null
                        && status.endsAt().isAfter(status.startsAt().plusDays(13))
                        && status.endsAt().isBefore(status.startsAt().plusDays(15))
                        && "Payments outage".equals(status.reason()))
                .verifyComplete();

        verify(adminAuditService).record(eq(42L), eq("START_PLATFORM_FREE_ACCESS"),
                eq("PLATFORM_ACCESS"), eq(PlatformAccessSettings.GLOBAL_ID), any(), any(), any());
    }

    @Test
    void end_disablesActiveFreeAccessImmediatelyAndAuditsChange() {
        PlatformAccessSettings settings = activeSettings();
        when(settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)).thenReturn(Mono.just(settings));
        when(settingsRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.end(null, ADMIN_USERNAME))
                .expectNextMatches(status -> !status.enabled()
                        && status.endsAt() != null
                        && !status.endsAt().isAfter(LocalDateTime.now()))
                .verifyComplete();

        verify(adminAuditService).record(eq(42L), eq("END_PLATFORM_FREE_ACCESS"),
                eq("PLATFORM_ACCESS"), eq(PlatformAccessSettings.GLOBAL_ID), any(), any(), any());
    }

    @Test
    void end_rejectsWhenFreeAccessIsNotActive() {
        PlatformAccessSettings settings = activeSettings();
        settings.setFreeAccessEndsAt(LocalDateTime.now().minusMinutes(1));
        when(settingsRepository.findById(PlatformAccessSettings.GLOBAL_ID)).thenReturn(Mono.just(settings));

        StepVerifier.create(service.end(null, ADMIN_USERNAME))
                .expectError(InvalidRecord.class)
                .verify();

        verify(settingsRepository, never()).save(any());
    }

    @Test
    void start_rejectsMoreThan30DaysOrNoReason() {
        StepVerifier.create(service.start(31, "x", ADMIN_USERNAME)).expectError(InvalidRecord.class).verify();
        StepVerifier.create(service.start(7, " ", ADMIN_USERNAME)).expectError(InvalidRecord.class).verify();
        verify(settingsRepository, never()).save(any());
    }

    private PlatformAccessSettings activeSettings() {
        PlatformAccessSettings settings = new PlatformAccessSettings();
        settings.setId(PlatformAccessSettings.GLOBAL_ID);
        settings.setFreeAccessStartedAt(LocalDateTime.now().minusDays(1));
        settings.setFreeAccessEndsAt(LocalDateTime.now().plusDays(10));
        settings.setUpdatedAt(LocalDateTime.now());
        return settings;
    }
}
