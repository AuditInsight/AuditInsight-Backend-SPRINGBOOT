package com.diana.auditinsightbackendspringboot.Repositories;

import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the Super Admin payments-ledger SQL against a real (H2, PostgreSQL mode) database. */
@SpringBootTest
@ActiveProfiles("test")
class PaymentLedgerQueryTest {

    @DynamicPropertySource
    static void overrideDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url",
                () -> "r2dbc:h2:mem:///payment-ledger-query-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE");
    }

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private DatabaseClient db;
    @MockitoBean private EmailService emailService;

    private final UUID acme = UUID.randomUUID();
    private final UUID beta = UUID.randomUUID();
    private static final LocalDateTime NOV_20 = LocalDateTime.of(2026, 11, 20, 10, 0);

    @BeforeEach
    void seed() {
        db.sql("DELETE FROM payments").then().block();
        db.sql("DELETE FROM organisation").then().block();
        db.sql("DELETE FROM users WHERE id IN (9001, 9002)").then().block();

        insertUser(9001, "aline@acme.org", "Aline Uwase");
        insertUser(9002, "jean@beta.org", "Jean Mugabo");
        insertOrganisation(acme, "Acme NGO");
        insertOrganisation(beta, "Beta Cooperative");

        insertPayment(acme, 9001L, "MOMO", "SUCCESSFUL", "0788123456", "MTN-111", NOV_20);
        insertPayment(acme, 9001L, "MOMO", "FAILED", "0788123456", null, NOV_20.plusDays(1));
        insertPayment(beta, 9002L, "CARD", "SUCCESSFUL", null, "AI-card-ref", NOV_20.plusDays(2));
        insertPayment(beta, 9002L, "MOMO", "PENDING", "0791112223", null, NOV_20.plusDays(3));
    }

    private List<Payment> page(String status, String provider, UUID org, LocalDateTime from, LocalDateTime to, String search) {
        return paymentRepository.findLedgerPage(status, provider, org, from, to, search, 50, 0).collectList().block();
    }

    private long count(String status, String provider, UUID org, LocalDateTime from, LocalDateTime to, String search) {
        return paymentRepository.countLedger(status, provider, org, from, to, search).block();
    }

    @Test
    void noFilters_returnsEveryPaymentNewestFirst() {
        List<Payment> all = page(null, null, null, null, null, null);

        assertThat(all).hasSize(4);
        assertThat(all.get(0).getCreatedAt()).isEqualTo(NOV_20.plusDays(3));
        assertThat(count(null, null, null, null, null, null)).isEqualTo(4);
    }

    @Test
    void statusAndProviderFilters() {
        assertThat(page("SUCCESSFUL", null, null, null, null, null)).hasSize(2);
        assertThat(page("SUCCESSFUL", "MOMO", null, null, null, null)).extracting(Payment::getProviderTransactionId)
                .containsExactly("MTN-111");
        assertThat(count("PENDING", null, null, null, null, null)).isEqualTo(1);
    }

    @Test
    void searchMatchesOrganisationPayerPhoneAndReference() {
        assertThat(page(null, null, null, null, null, "beta coop")).hasSize(2);
        assertThat(page(null, null, null, null, null, "ALINE")).hasSize(2);
        assertThat(page(null, null, null, null, null, "jean@beta")).hasSize(2);
        assertThat(page(null, null, null, null, null, "0791112")).hasSize(1);
        assertThat(page(null, null, null, null, null, "mtn-111")).hasSize(1);
        assertThat(count(null, null, null, null, null, "nobody")).isZero();
    }

    @Test
    void organisationAndDateRange_fromInclusiveToExclusive() {
        assertThat(page(null, null, acme, null, null, null)).hasSize(2);
        assertThat(page(null, null, null, NOV_20.plusDays(1), NOV_20.plusDays(3), null)).hasSize(2);
    }

    @Test
    void pagination() {
        List<Payment> second = paymentRepository.findLedgerPage(null, null, null, null, null, null, 3, 3)
                .collectList().block();

        assertThat(second).hasSize(1);
        assertThat(second.get(0).getCreatedAt()).isEqualTo(NOV_20);
    }

    @Test
    void totals_countEveryStatusAndAddUpMoneyReceived() {
        db.sql("UPDATE payments SET received_amount = 15000 WHERE status = 'SUCCESSFUL'").then().block();

        var totals = paymentRepository.ledgerTotals(null, null, null, null, null, null).collectList().block();

        assertThat(totals).extracting(t -> t.getStatus() + ":" + t.getPayments() + ":" + t.getReceived().intValue())
                .containsExactlyInAnyOrder("FAILED:1:0", "PENDING:1:0", "SUCCESSFUL:2:30000");
        assertThat(paymentRepository.ledgerTotals(null, null, acme, null, null, null).collectList().block())
                .extracting(t -> t.getStatus() + ":" + t.getReceived().intValue())
                .containsExactlyInAnyOrder("FAILED:0", "SUCCESSFUL:15000");
    }

    private void insertUser(long id, String email, String name) {
        db.sql("INSERT INTO users (id, username, full_name, role, verified, active, must_change_password) "
                        + "VALUES (:id, :email, :name, 'CLIENT', TRUE, TRUE, FALSE)")
                .bind("id", id).bind("email", email).bind("name", name).then().block();
    }

    private void insertOrganisation(UUID id, String name) {
        db.sql("INSERT INTO organisation (id, client_id, name) VALUES (:id, :clientId, :name)")
                .bind("id", id).bind("clientId", UUID.randomUUID()).bind("name", name).then().block();
    }

    private void insertPayment(UUID org, Long payer, String provider, String status, String phone, String reference,
                               LocalDateTime createdAt) {
        var spec = db.sql("INSERT INTO payments (organisation_id, subscription_type, provider, status, expected_amount, "
                        + "currency, payer_phone, provider_transaction_id, created_by, created_at) "
                        + "VALUES (:org, 'MONTHLY', :provider, :status, 15000, 'RWF', :phone, :ref, :payer, :createdAt)")
                .bind("org", org).bind("provider", provider).bind("status", status)
                .bind("payer", payer).bind("createdAt", createdAt);
        spec = phone == null ? spec.bindNull("phone", String.class) : spec.bind("phone", phone);
        spec = reference == null ? spec.bindNull("ref", String.class) : spec.bind("ref", reference);
        spec.then().block();
    }
}
