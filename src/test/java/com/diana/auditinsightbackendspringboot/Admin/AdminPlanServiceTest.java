package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminUpdatePlanPriceRequest;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.AdminAuditLog;
import com.diana.auditinsightbackendspringboot.Models.PlanPrice;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.PlanPriceRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPlanServiceTest {

    @Mock private PlanPriceRepository planPriceRepository;
    @Mock private UserRepository userRepository;
    @Mock private AdminAuditService adminAuditService;

    private AdminPlanService adminPlanService;

    private static final String ACTING_ADMIN = "info@auditinsight.tech";

    @BeforeEach
    void setUp() {
        adminPlanService = new AdminPlanService(planPriceRepository, userRepository, adminAuditService);
    }

    private PlanPrice existingMonthly() {
        PlanPrice p = new PlanPrice();
        p.setSubscriptionType(SubscriptionType.MONTHLY);
        p.setPrice(new BigDecimal("15000"));
        p.setCurrency("RWF");
        p.setDurationDays(30);
        p.setUpdatedAt(LocalDateTime.now().minusDays(10));
        return p;
    }

    @Test
    void updatePrice_validRequest_savesNewValuesAndRecordsAudit() {
        when(planPriceRepository.findById(SubscriptionType.MONTHLY)).thenReturn(Mono.just(existingMonthly()));
        User admin = new User();
        admin.setId(1L);
        admin.setUsername(ACTING_ADMIN);
        when(userRepository.findByUsername(ACTING_ADMIN)).thenReturn(Mono.just(admin));
        when(userRepository.findById(1L)).thenReturn(Mono.just(admin));
        when(planPriceRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(adminAuditService.record(eq(1L), eq("UPDATE_PLAN_PRICE"), eq("PLAN_PRICE"),
                eq("MONTHLY"), any(), any())).thenReturn(Mono.just(new AdminAuditLog()));

        AdminUpdatePlanPriceRequest request = new AdminUpdatePlanPriceRequest();
        request.setPrice(new BigDecimal("20000"));
        request.setDurationDays(45);

        StepVerifier.create(adminPlanService.updatePrice(SubscriptionType.MONTHLY, request, ACTING_ADMIN))
                .expectNextMatches(response -> response.getPrice().compareTo(new BigDecimal("20000")) == 0
                        && response.getDurationDays() == 45
                        && "info@auditinsight.tech".equals(response.getUpdatedByUsername()))
                .verifyComplete();

        ArgumentCaptor<PlanPrice> captor = ArgumentCaptor.forClass(PlanPrice.class);
        verify(planPriceRepository).save(captor.capture());
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("20000");
        assertThat(captor.getValue().getDurationDays()).isEqualTo(45);
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(1L);

        verify(adminAuditService).record(eq(1L), eq("UPDATE_PLAN_PRICE"), eq("PLAN_PRICE"), eq("MONTHLY"), any(), any());
    }

    @Test
    void updatePrice_unknownSubscriptionType_emitsInvalidRecord() {
        when(planPriceRepository.findById(SubscriptionType.MONTHLY)).thenReturn(Mono.empty());

        AdminUpdatePlanPriceRequest request = new AdminUpdatePlanPriceRequest();
        request.setPrice(new BigDecimal("20000"));
        request.setDurationDays(45);

        StepVerifier.create(adminPlanService.updatePrice(SubscriptionType.MONTHLY, request, ACTING_ADMIN))
                .expectError(InvalidRecord.class)
                .verify();

        verify(planPriceRepository, never()).save(any());
    }
}
