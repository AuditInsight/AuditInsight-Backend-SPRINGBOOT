package com.diana.auditinsightbackendspringboot.Services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SignedDocumentServiceTest {

    private final TimeZone originalZone = TimeZone.getDefault();

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    void toUtc_serverInKigali_shiftsBackTwoHours() {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Kigali")); // UTC+2
        assertEquals(LocalDateTime.of(2026, 11, 7, 8, 30),
                SignedDocumentService.toUtc(LocalDateTime.of(2026, 11, 7, 10, 30)));
    }

    @Test
    void toUtc_serverAlreadyInUtc_leavesTimeUnchanged() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        LocalDateTime signedAt = LocalDateTime.of(2026, 11, 7, 10, 30);
        assertEquals(signedAt, SignedDocumentService.toUtc(signedAt));
    }
}
