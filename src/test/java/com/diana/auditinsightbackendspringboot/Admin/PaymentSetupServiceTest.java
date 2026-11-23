package com.diana.auditinsightbackendspringboot.Admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSetupServiceTest {

    private static PaymentSetupService setup(String baseUrl, String token, String provider, String flwKey,
                                             String flwHash, String flwRedirect) {
        return new PaymentSetupService(baseUrl, token, provider, flwKey, flwHash, flwRedirect);
    }

    @Test
    void liveAndComplete_isReady_andNeverExposesSecrets() {
        var status = setup("https://api.pawapay.io/", "live-token-secret", "MTN_MOMO_RWA",
                "FLWSECK-live-secret", "hash-secret", "https://app.example/billing").status();

        assertThat(status.ready()).isTrue();
        assertThat(status.mobileMoney().environment()).isEqualTo(PaymentSetupService.Environment.LIVE);
        assertThat(status.card().environment()).isEqualTo(PaymentSetupService.Environment.LIVE);
        assertThat(status.problems()).isEmpty();
        assertThat(status.toString()).doesNotContain("live-token-secret", "FLWSECK-live-secret", "hash-secret");
    }

    @Test
    void sandbox_isReadyButWarnsThatNoRealMoneyMoves() {
        var status = setup("https://api.sandbox.pawapay.io", "t", "MTN_MOMO_RWA",
                "FLWSECK_TEST-abc", "h", "https://app.example").status();

        assertThat(status.ready()).isTrue();
        assertThat(status.mobileMoney().environment()).isEqualTo(PaymentSetupService.Environment.SANDBOX);
        assertThat(status.card().environment()).isEqualTo(PaymentSetupService.Environment.SANDBOX);
        assertThat(status.warnings()).anyMatch(w -> w.contains("SANDBOX")).anyMatch(w -> w.contains("TEST key"));
    }

    @Test
    void missingTokenOrUnknownAddress_isNotReady() {
        var status = setup("https://pawapay.example", " ", "MTN_MOMO_RWA", "", "", "").status();

        assertThat(status.ready()).isFalse();
        assertThat(status.mobileMoney().tokenSet()).isFalse();
        assertThat(status.problems()).anyMatch(p -> p.contains("PAWAPAY_API_TOKEN"))
                .anyMatch(p -> p.contains("PAWAPAY_API_BASE_URL"))
                .anyMatch(p -> p.contains("FLUTTERWAVE_SECRET_KEY"))
                .anyMatch(p -> p.contains("FLUTTERWAVE_WEBHOOK_SECRET_HASH"));
    }

    @Test
    void nonMtnProvider_warns() {
        var status = setup("https://api.pawapay.io", "t", "AIRTEL_RWA", "k", "h", "r").status();

        assertThat(status.warnings()).anyMatch(w -> w.contains("AIRTEL_RWA"));
    }
}
