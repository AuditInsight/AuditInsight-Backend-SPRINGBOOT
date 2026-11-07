package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.AccessBasis;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.TrialGrantStatus;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Models.TrialGrant;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntitlementServiceTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 11, 7, 12, 0);

    @Test
    void noPaidPeriodNoTrialNoOverride_requiresPayment() {
        EntitlementService.Entitlement e = EntitlementService.decide(now, List.of(), null, null);
        assertThat(e.basis()).isEqualTo(AccessBasis.PAYMENT_REQUIRED);
        assertThat(e.hasAccess()).isFalse();
    }

    @Test
    void currentPaidPeriod_givesPaidAccess() {
        Subscription paid = period(now.minusDays(5), now.plusDays(25));
        EntitlementService.Entitlement e = EntitlementService.decide(now, List.of(paid), null, null);
        assertThat(e.basis()).isEqualTo(AccessBasis.PAID);
        assertThat(e.accessUntil()).isEqualTo(paid.getEndDate());
    }

    @Test
    void paidWinsOverTrialAndOverride() {
        Subscription paid = period(now.minusDays(1), now.plusDays(10));
        EntitlementService.Entitlement e = EntitlementService.decide(now, List.of(paid),
                trial(now.minusDays(1), now.plusDays(3)), now.plusDays(2));
        assertThat(e.basis()).isEqualTo(AccessBasis.PAID);
    }

    @Test
    void backToBackPaidPeriods_accessRunsToTheLastEnd() {
        Subscription first = period(now.minusDays(10), now.plusDays(20));
        Subscription second = period(now.plusDays(20), now.plusDays(50));
        EntitlementService.Entitlement e = EntitlementService.decide(now, List.of(first, second), null, null);
        assertThat(e.accessUntil()).isEqualTo(second.getEndDate());
        assertThat(e.nextPaid()).isSameAs(second);
    }

    @Test
    void paidDuringTrial_trialGivesAccessAndPaidPeriodIsNext() {
        TrialGrant trial = trial(now.minusDays(2), now.plusDays(5));
        Subscription scheduled = period(trial.getEndsAt(), trial.getEndsAt().plusDays(30));
        EntitlementService.Entitlement e = EntitlementService.decide(now, List.of(scheduled), trial, null);
        assertThat(e.basis()).isEqualTo(AccessBasis.TRIAL);
        assertThat(e.accessUntil()).isEqualTo(trial.getEndsAt());
        assertThat(e.nextPaid()).isSameAs(scheduled);
    }

    @Test
    void expiredTrial_givesNoAccess() {
        EntitlementService.Entitlement e = EntitlementService.decide(now, List.of(),
                trial(now.minusDays(8), now.minusMinutes(1)), null);
        assertThat(e.basis()).isEqualTo(AccessBasis.PAYMENT_REQUIRED);
    }

    @Test
    void revokedTrial_givesNoAccess() {
        TrialGrant trial = trial(now.minusDays(1), now.plusDays(5));
        trial.setStatus(TrialGrantStatus.REVOKED);
        EntitlementService.Entitlement e = EntitlementService.decide(now, List.of(), trial, null);
        assertThat(e.basis()).isEqualTo(AccessBasis.PAYMENT_REQUIRED);
    }

    @Test
    void emergencyOverride_isUsedOnlyWithoutPaidOrTrial() {
        EntitlementService.Entitlement e = EntitlementService.decide(now, List.of(), null, now.plusDays(1));
        assertThat(e.basis()).isEqualTo(AccessBasis.EMERGENCY_OVERRIDE);
        assertThat(e.accessUntil()).isEqualTo(now.plusDays(1));
    }

    @Test
    void periodEndingExactlyNow_isNotCurrent() {
        assertThat(EntitlementService.isCurrent(period(now.minusDays(30), now), now)).isFalse();
        assertThat(EntitlementService.isCurrent(period(now, now.plusDays(30)), now)).isTrue();
    }

    private Subscription period(LocalDateTime start, LocalDateTime end) {
        Subscription s = new Subscription();
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setStartDate(start);
        s.setEndDate(end);
        return s;
    }

    private TrialGrant trial(LocalDateTime start, LocalDateTime end) {
        TrialGrant g = new TrialGrant();
        g.setStatus(TrialGrantStatus.ACTIVE);
        g.setStartsAt(start);
        g.setEndsAt(end);
        return g;
    }
}
