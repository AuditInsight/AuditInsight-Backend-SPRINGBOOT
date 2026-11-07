package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Admin.AdminAuditService;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.AdminAuditLog;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Models.TrialCampaign;
import com.diana.auditinsightbackendspringboot.Models.TrialGrant;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.TrialCampaignRepository;
import com.diana.auditinsightbackendspringboot.Repositories.TrialGrantRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialCampaignServiceTest {

    @Mock private TrialCampaignRepository campaignRepo;
    @Mock private TrialGrantRepository grantRepo;
    @Mock private TrialGrantService trialGrantService;
    @Mock private UserRepository userRepo;
    @Mock private AdminAuditService adminAuditService;

    private TrialCampaignService service;
    private static final String ADMIN = "admin@auditinsight.test";

    @BeforeEach
    void setUp() {
        service = new TrialCampaignService(campaignRepo, grantRepo, trialGrantService, userRepo, adminAuditService);
        User admin = new User();
        admin.setId(7L);
        lenient().when(userRepo.findByUsername(ADMIN)).thenReturn(Mono.just(admin));
        lenient().when(campaignRepo.save(any())).thenAnswer(inv -> {
            TrialCampaign c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return Mono.just(c);
        });
        lenient().when(grantRepo.countByCampaignId(any())).thenReturn(Mono.just(0L));
        lenient().when(adminAuditService.record(any(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(Mono.just(new AdminAuditLog()));
    }

    @Test
    void create_isDisabledUntilEnabled() {
        StepVerifier.create(service.create("Launch", 30, null, null, "Launch month", ADMIN))
                .assertNext(v -> {
                    assertThat(v.campaign().isEnabled()).isFalse();
                    assertThat(v.grantingNow()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void enable_requiresReason() {
        StepVerifier.create(service.enable(UUID.randomUUID(), " ", ADMIN)).expectError(InvalidRecord.class).verify();
    }

    @Test
    void enable_secondCampaignIsRejected() {
        TrialCampaign c = campaign(false, null);
        when(campaignRepo.findById(c.getId())).thenReturn(Mono.just(c));
        org.mockito.Mockito.doReturn(Mono.error(new DataIntegrityViolationException("unique")))
                .when(campaignRepo).save(any());

        StepVerifier.create(service.enable(c.getId(), "Go", ADMIN))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("already enabled"))
                .verify();
    }

    @Test
    void registration_grantsOnlyWhileCampaignIsInItsWindow() {
        Organisation org = new Organisation();
        org.setId(UUID.randomUUID());
        TrialCampaign running = campaign(true, null);
        when(campaignRepo.findFirstByEnabledTrue()).thenReturn(Mono.just(running));
        when(trialGrantService.grantFromCampaign(org, running)).thenReturn(Mono.just(new TrialGrant()));

        StepVerifier.create(service.grantOnRegistration(org)).expectNextCount(1).verifyComplete();
    }

    @Test
    void registration_noGrantAfterCampaignWindowEnds() {
        Organisation org = new Organisation();
        TrialCampaign ended = campaign(true, LocalDateTime.now().minusHours(1));
        when(campaignRepo.findFirstByEnabledTrue()).thenReturn(Mono.just(ended));

        StepVerifier.create(service.grantOnRegistration(org)).verifyComplete();
        verify(trialGrantService, never()).grantFromCampaign(any(), any());
    }

    @Test
    void registration_neverFailsBecauseOfTheCampaign() {
        Organisation org = new Organisation();
        when(campaignRepo.findFirstByEnabledTrue()).thenReturn(Mono.error(new RuntimeException("db down")));

        StepVerifier.create(service.grantOnRegistration(org)).verifyComplete();
    }

    private TrialCampaign campaign(boolean enabled, LocalDateTime endsAt) {
        TrialCampaign c = new TrialCampaign();
        c.setId(UUID.randomUUID());
        c.setName("Launch");
        c.setDurationDays(30);
        c.setStartsAt(LocalDateTime.now().minusDays(2));
        c.setEndsAt(endsAt);
        c.setEnabled(enabled);
        c.setCreatedBy(7L);
        return c;
    }
}
