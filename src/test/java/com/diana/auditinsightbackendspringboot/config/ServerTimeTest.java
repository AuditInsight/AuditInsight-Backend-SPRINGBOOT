package com.diana.auditinsightbackendspringboot.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ServerTimeTest {

    @Test
    void utcServerTime_isMarkedAsUtc_soKigaliShowsTwoHoursLater() {
        OffsetDateTime result = ServerTime.withOffset(LocalDateTime.of(2026, 11, 20, 16, 3), ZoneOffset.UTC);

        assertThat(result.toString()).isEqualTo("2026-11-20T16:03Z");
        assertThat(result.atZoneSameInstant(ZoneId.of("Africa/Kigali")).getHour()).isEqualTo(18);
    }

    @Test
    void nonUtcServer_keepsTheSameInstant() {
        OffsetDateTime result = ServerTime.withOffset(LocalDateTime.of(2026, 11, 20, 18, 3), ZoneId.of("Africa/Kigali"));

        assertThat(result.toInstant()).isEqualTo(OffsetDateTime.parse("2026-11-20T16:03Z").toInstant());
    }

    @Test
    void missingTime_staysMissing() {
        assertThat(ServerTime.withOffset(null)).isNull();
    }
}
