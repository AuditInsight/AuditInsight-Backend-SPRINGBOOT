package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.PaymentLedgerTotal;
import com.diana.auditinsightbackendspringboot.Enum.PaymentProvider;
import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private UserRepository userRepository;

    private AdminPaymentService service;
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminPaymentService(paymentRepository, organisationRepository, userRepository);
    }

    private static AdminPaymentService.Filters none() {
        return new AdminPaymentService.Filters(null, null, null, null, null, null);
    }

    private Payment payment(Long payerId) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setOrganisationId(orgId);
        p.setSubscriptionType(SubscriptionType.MONTHLY);
        p.setProvider(PaymentProvider.MOMO);
        p.setStatus(PaymentStatus.SUCCESSFUL);
        p.setExpectedAmount(new BigDecimal("15000"));
        p.setReceivedAmount(new BigDecimal("15000"));
        p.setCurrency("RWF");
        p.setCreatedBy(payerId);
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }

    @Test
    void list_fillsInPayerAndOrganisationWithOneLookupEach() {
        when(paymentRepository.findLedgerPage(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(AdminPaymentService.PAGE_SIZE), eq(0L)))
                .thenReturn(Flux.just(payment(7L), payment(7L), payment(null)));
        when(paymentRepository.countLedger(any(), any(), any(), any(), any(), any())).thenReturn(Mono.just(3L));
        User payer = new User();
        payer.setId(7L);
        payer.setUsername("aline@acme.org");
        payer.setFullName("Aline Uwase");
        when(userRepository.findAllById(any(Iterable.class))).thenReturn(Flux.just(payer));
        Organisation org = new Organisation();
        org.setId(orgId);
        org.setName("Acme NGO");
        when(organisationRepository.findAllById(any(Iterable.class))).thenReturn(Flux.just(org));

        StepVerifier.create(service.list(none(), 1))
                .assertNext(page -> {
                    assertThat(page.getTotalItems()).isEqualTo(3);
                    assertThat(page.getItems()).hasSize(3);
                    assertThat(page.getItems().get(0).getPayerName()).isEqualTo("Aline Uwase");
                    assertThat(page.getItems().get(0).getOrganisationName()).isEqualTo("Acme NGO");
                    assertThat(page.getItems().get(2).getPayerName()).isNull();
                })
                .verifyComplete();

        verify(userRepository, times(1)).findAllById(any(Iterable.class));
        verify(organisationRepository, times(1)).findAllById(any(Iterable.class));
    }

    @Test
    void list_pageTwoSkipsTheFirstPage() {
        when(paymentRepository.findLedgerPage(any(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Flux.empty());
        when(paymentRepository.countLedger(any(), any(), any(), any(), any(), any())).thenReturn(Mono.just(0L));

        StepVerifier.create(service.list(none(), 2)).expectNextCount(1).verifyComplete();

        verify(paymentRepository).findLedgerPage(any(), any(), any(), any(), any(), any(),
                eq(AdminPaymentService.PAGE_SIZE), eq((long) AdminPaymentService.PAGE_SIZE));
    }

    @Test
    void filters_statusAndMethodAreCaseInsensitive_andUnknownOnesAreRejected() {
        StepVerifier.create(service.validate(new AdminPaymentService.Filters("successful", "momo", null, null, null, null)))
                .assertNext(q -> {
                    assertThat(q.status()).isEqualTo("SUCCESSFUL");
                    assertThat(q.provider()).isEqualTo("MOMO");
                })
                .verifyComplete();

        StepVerifier.create(service.validate(new AdminPaymentService.Filters("PAID", null, null, null, null, null)))
                .expectError(InvalidRecord.class).verify();
        StepVerifier.create(service.validate(new AdminPaymentService.Filters(null, "PAYPAL", null, null, null, null)))
                .expectError(InvalidRecord.class).verify();
    }

    @Test
    void filters_dateRangeIsConvertedToServerTime_andMustBeInOrder() {
        OffsetDateTime kigaliMidnight = OffsetDateTime.parse("2026-11-20T00:00+02:00");

        StepVerifier.create(service.validate(new AdminPaymentService.Filters(null, null, null, kigaliMidnight,
                        kigaliMidnight.plusDays(1), null)))
                .assertNext(q -> assertThat(q.from()).isEqualTo(
                        kigaliMidnight.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()))
                .verifyComplete();

        StepVerifier.create(service.validate(new AdminPaymentService.Filters(null, null, null, kigaliMidnight,
                        kigaliMidnight, null)))
                .expectError(InvalidRecord.class).verify();
    }

    @Test
    void search_internationalPhoneNumbersMatchTheStoredLocalForm() {
        assertThat(AdminPaymentService.normaliseSearch("+250 788 123 456")).isEqualTo("0788123456");
        assertThat(AdminPaymentService.normaliseSearch("250788123456")).isEqualTo("0788123456");
        assertThat(AdminPaymentService.normaliseSearch("0788 123")).isEqualTo("0788123");
        assertThat(AdminPaymentService.normaliseSearch("  Acme NGO ")).isEqualTo("Acme NGO");
        assertThat(AdminPaymentService.normaliseSearch("   ")).isNull();
    }

    @Test
    void totals_passTheSameFilters() {
        when(paymentRepository.ledgerTotals(eq("SUCCESSFUL"), isNull(), eq(orgId), isNull(), isNull(), isNull()))
                .thenReturn(Flux.just(new PaymentLedgerTotal("SUCCESSFUL", "RWF", 2L, new BigDecimal("30000"))));

        StepVerifier.create(service.totals(new AdminPaymentService.Filters("SUCCESSFUL", null, orgId, null, null, null)))
                .assertNext(totals -> assertThat(totals).singleElement()
                        .satisfies(t -> assertThat(t.getReceived()).isEqualByComparingTo("30000")))
                .verifyComplete();
    }
}
