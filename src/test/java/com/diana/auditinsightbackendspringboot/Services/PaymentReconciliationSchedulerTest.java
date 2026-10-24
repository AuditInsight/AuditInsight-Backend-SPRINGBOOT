package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.PaymentProvider;
import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationSchedulerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;

    private PaymentReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentReconciliationScheduler(paymentRepository, paymentService);
        ReflectionTestUtils.setField(scheduler, "staleAfterMinutes", 15L);
        ReflectionTestUtils.setField(scheduler, "expireAfterHours", 24L);
    }

    private Payment pendingPayment(LocalDateTime createdAt) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setProvider(PaymentProvider.MOMO);
        p.setStatus(PaymentStatus.PENDING);
        p.setCreatedAt(createdAt);
        return p;
    }

    @Test
    void reconcilePendingPayments_recentlyStale_reconcilesWithProviderInsteadOfExpiring() {
        Payment stale = pendingPayment(LocalDateTime.now().minusMinutes(20)); // stale but well under 24h
        when(paymentRepository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.PENDING), any()))
                .thenReturn(Flux.just(stale));
        when(paymentService.reconcilePendingPayment(stale)).thenReturn(Mono.just(stale));

        scheduler.reconcilePendingPayments();

        verify(paymentService, timeout(1000)).reconcilePendingPayment(stale);
        verify(paymentService, never()).expireStalePayment(any());
    }

    @Test
    void reconcilePendingPayments_veryOld_expiresWithoutCallingProvider() {
        Payment veryOld = pendingPayment(LocalDateTime.now().minusHours(25)); // past the 24h expiry cutoff
        when(paymentRepository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.PENDING), any()))
                .thenReturn(Flux.just(veryOld));
        when(paymentService.expireStalePayment(veryOld)).thenReturn(Mono.just(veryOld));

        scheduler.reconcilePendingPayments();

        verify(paymentService, timeout(1000)).expireStalePayment(veryOld);
        verify(paymentService, never()).reconcilePendingPayment(any());
    }

    @Test
    void reconcilePendingPayments_oneFailureDoesNotStopTheSweep() {
        Payment failing = pendingPayment(LocalDateTime.now().minusMinutes(20));
        Payment succeeding = pendingPayment(LocalDateTime.now().minusMinutes(30));
        when(paymentRepository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.PENDING), any()))
                .thenReturn(Flux.just(failing, succeeding));
        when(paymentService.reconcilePendingPayment(failing))
                .thenReturn(Mono.error(new RuntimeException("pawaPay status API unreachable")));
        when(paymentService.reconcilePendingPayment(succeeding)).thenReturn(Mono.just(succeeding));

        scheduler.reconcilePendingPayments();

        verify(paymentService, timeout(1000)).reconcilePendingPayment(succeeding);
    }
}
