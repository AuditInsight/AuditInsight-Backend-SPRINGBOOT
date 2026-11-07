package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.DTOs.AdminSubscriptionSummary;
import com.diana.auditinsightbackendspringboot.Models.AdminAuditLog;
import com.diana.auditinsightbackendspringboot.Repositories.AdminAuditLogRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAuditServiceTest {

    private final AdminAuditLogRepository repo = mock(AdminAuditLogRepository.class);
    private final AdminAuditService service = new AdminAuditService(repo);

    @Test
    void subscriptionStateWithDates_isStoredAsReadableJson() {
        when(repo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        AdminSubscriptionSummary after = new AdminSubscriptionSummary();
        after.setEndDate(LocalDateTime.of(2026, 12, 15, 9, 30));

        AdminAuditLog entry = service.record(1L, "CHANGE_SUBSCRIPTION", "ORGANISATION", "org-1",
                null, after, "  goodwill  ").block();

        assertTrue(entry.getAfterState().contains("\"endDate\":\"2026-12-15T09:30:00\""),
                "dates must be ISO-8601 text, got: " + entry.getAfterState());
        assertEquals("goodwill", entry.getReason());
    }

    @Test
    void offsetDateTimes_andMaps_serialiseCleanly() {
        String json = service.toJson(Map.of("endsAt", OffsetDateTime.of(2026, 12, 15, 0, 0, 0, 0, ZoneOffset.UTC)));
        assertEquals("{\"endsAt\":\"2026-12-15T00:00:00Z\"}", json);
    }

    @Test
    void blankReason_isStoredAsNull() {
        when(repo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        AdminAuditLog entry = service.record(1L, "X", "Y", "z", null, null, "   ").block();
        assertEquals(null, entry.getReason());
    }
}
