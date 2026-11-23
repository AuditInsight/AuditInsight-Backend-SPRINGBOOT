package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminPaymentResponse;
import com.diana.auditinsightbackendspringboot.DTOs.PageResult;
import com.diana.auditinsightbackendspringboot.DTOs.PaymentLedgerTotal;
import com.diana.auditinsightbackendspringboot.Enum.PaymentProvider;
import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import com.diana.auditinsightbackendspringboot.Models.Organisation;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.OrganisationRepository;
import com.diana.auditinsightbackendspringboot.Repositories.PaymentRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The Super Admin's view of every payment on the platform: who paid what, when, how, and how it ended. */
@Service
public class AdminPaymentService {

    static final int PAGE_SIZE = 25;

    private final PaymentRepository paymentRepository;
    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;

    public AdminPaymentService(PaymentRepository paymentRepository, OrganisationRepository organisationRepository,
                               UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.organisationRepository = organisationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Ledger filters, all optional. {@code from}/{@code to} bound when the payment was started ({@code from}
     * inclusive, {@code to} exclusive) and carry the viewer's time zone.
     */
    public record Filters(String status, String provider, UUID organisationId, OffsetDateTime from,
                          OffsetDateTime to, String search) {}

    /** The filters as the database stores them: enum names, server-clock times, a normalised search term. */
    record QueryFilters(String status, String provider, UUID organisationId, LocalDateTime from, LocalDateTime to,
                        String search) {}

    public Mono<PageResult<AdminPaymentResponse>> list(Filters filters, int page) {
        int safePage = Math.max(page, 1);
        long offset = (long) (safePage - 1) * PAGE_SIZE;
        return validate(filters).flatMap(q -> paymentRepository
                .findLedgerPage(q.status(), q.provider(), q.organisationId(), q.from(), q.to(), q.search(),
                        PAGE_SIZE, offset)
                .collectList()
                .flatMap(this::toResponses)
                .zipWith(paymentRepository.countLedger(q.status(), q.provider(), q.organisationId(), q.from(), q.to(),
                        q.search()))
                .map(t -> PageResult.of(t.getT1(), t.getT2(), safePage, PAGE_SIZE)));
    }

    public Mono<List<PaymentLedgerTotal>> totals(Filters filters) {
        return validate(filters).flatMap(q -> paymentRepository
                .ledgerTotals(q.status(), q.provider(), q.organisationId(), q.from(), q.to(), q.search())
                .collectList());
    }

    Mono<QueryFilters> validate(Filters f) {
        String status;
        String provider;
        try {
            status = blank(f.status()) ? null : PaymentStatus.valueOf(f.status().trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            return Mono.error(new InvalidRecord("Unknown payment status: " + f.status()));
        }
        try {
            provider = blank(f.provider()) ? null : PaymentProvider.valueOf(f.provider().trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            return Mono.error(new InvalidRecord("Unknown payment method: " + f.provider()));
        }
        LocalDateTime from = toServerTime(f.from());
        LocalDateTime to = toServerTime(f.to());
        if (from != null && to != null && !to.isAfter(from)) {
            return Mono.error(new InvalidRecord("The end of the date range must be after its start"));
        }
        return Mono.just(new QueryFilters(status, provider, f.organisationId(), from, to, normaliseSearch(f.search())));
    }

    /**
     * Trims the search term. A Rwandan phone number written internationally (+250 788 123 456 or 250788123456) is
     * turned into the local form the payments table stores (0788123456).
     */
    static String normaliseSearch(String search) {
        if (blank(search)) return null;
        String trimmed = search.trim();
        String compact = trimmed.replaceAll("[\\s+()-]", "");
        if (compact.matches("250\\d{9}")) {
            return "0" + compact.substring(3);
        }
        if (compact.matches("0?7\\d{1,8}")) {
            return compact;
        }
        return trimmed;
    }

    private static LocalDateTime toServerTime(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** Looks up the page's payers and organisations in two queries instead of two per payment. */
    private Mono<List<AdminPaymentResponse>> toResponses(List<Payment> payments) {
        List<Long> payerIds = payments.stream().map(Payment::getCreatedBy).filter(Objects::nonNull).distinct().toList();
        List<UUID> orgIds = payments.stream().map(Payment::getOrganisationId).filter(Objects::nonNull).distinct().toList();
        Mono<Map<Long, User>> payers = payerIds.isEmpty() ? Mono.just(Map.of())
                : userRepository.findAllById(payerIds).collectMap(User::getId, Function.identity());
        Mono<Map<UUID, String>> orgNames = orgIds.isEmpty() ? Mono.just(Map.of())
                : organisationRepository.findAllById(orgIds).collectMap(Organisation::getId, Organisation::getName);
        return Mono.zip(payers, orgNames).map(t -> payments.stream()
                .map(p -> AdminPaymentResponse.of(p, t.getT2().get(p.getOrganisationId()),
                        p.getCreatedBy() == null ? null : t.getT1().get(p.getCreatedBy())))
                .collect(Collectors.toList()));
    }
}
