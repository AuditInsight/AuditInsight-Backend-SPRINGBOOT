package com.diana.auditinsightbackendspringboot.Admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the server's own payment settings and says whether Mobile Money and card payments are set up, and whether they
 * point at live or test (sandbox) money. It never contacts pawaPay or Flutterwave and never shows a secret: only
 * whether one is set. A real payment is still the only end-to-end proof.
 */
@Service
@Slf4j
public class PaymentSetupService {

    static final String LIVE_PAWAPAY = "https://api.pawapay.io";
    static final String SANDBOX_PAWAPAY = "https://api.sandbox.pawapay.io";
    static final String PAWAPAY_CALLBACK_PATH = "/api/subscriptions/webhooks/pawapay";
    static final String FLUTTERWAVE_WEBHOOK_PATH = "/api/subscriptions/webhooks/flutterwave";

    private final String pawaPayBaseUrl;
    private final String pawaPayToken;
    private final String pawaPayProvider;
    private final String flutterwaveSecretKey;
    private final String flutterwaveWebhookHash;
    private final String flutterwaveRedirectUrl;

    public PaymentSetupService(@Value("${pawapay.api.base-url:}") String pawaPayBaseUrl,
                               @Value("${pawapay.api.token:}") String pawaPayToken,
                               @Value("${pawapay.provider:}") String pawaPayProvider,
                               @Value("${flutterwave.secret-key:}") String flutterwaveSecretKey,
                               @Value("${flutterwave.webhook-secret-hash:}") String flutterwaveWebhookHash,
                               @Value("${flutterwave.redirect-url:}") String flutterwaveRedirectUrl) {
        this.pawaPayBaseUrl = pawaPayBaseUrl;
        this.pawaPayToken = pawaPayToken;
        this.pawaPayProvider = pawaPayProvider;
        this.flutterwaveSecretKey = flutterwaveSecretKey;
        this.flutterwaveWebhookHash = flutterwaveWebhookHash;
        this.flutterwaveRedirectUrl = flutterwaveRedirectUrl;
    }

    public enum Environment { LIVE, SANDBOX, UNKNOWN }

    public record MobileMoney(Environment environment, String baseUrl, boolean tokenSet, String provider,
                              String callbackPath) {}

    public record Card(Environment environment, boolean secretKeySet, boolean webhookHashSet, String redirectUrl,
                       String webhookPath) {}

    /** {@code problems} would stop payments; {@code warnings} need a human to confirm they are intended. */
    public record SetupStatus(boolean ready, MobileMoney mobileMoney, Card card, List<String> problems,
                              List<String> warnings) {}

    public SetupStatus status() {
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String baseUrl = trimSlash(pawaPayBaseUrl);
        Environment momoEnv = LIVE_PAWAPAY.equals(baseUrl) ? Environment.LIVE
                : SANDBOX_PAWAPAY.equals(baseUrl) ? Environment.SANDBOX : Environment.UNKNOWN;
        boolean tokenSet = isSet(pawaPayToken);
        if (!tokenSet) problems.add("PAWAPAY_API_TOKEN is not set: Mobile Money payments cannot start.");
        if (momoEnv == Environment.UNKNOWN) {
            problems.add("PAWAPAY_API_BASE_URL is '" + baseUrl + "', which is neither " + LIVE_PAWAPAY
                    + " (live) nor " + SANDBOX_PAWAPAY + " (sandbox).");
        }
        if (momoEnv == Environment.SANDBOX) {
            warnings.add("Mobile Money is on the pawaPay SANDBOX: no real money moves. Use " + LIVE_PAWAPAY
                    + " and a live token to take real payments.");
        }
        if (!"MTN_MOMO_RWA".equals(pawaPayProvider)) {
            warnings.add("PAWAPAY_PROVIDER is '" + pawaPayProvider + "'. The checkout only accepts MTN Rwanda "
                    + "numbers, which need MTN_MOMO_RWA.");
        }
        warnings.add("The pawaPay token must match the environment (a sandbox token fails on live and the other "
                + "way round), and the pawaPay dashboard's deposit callback URL should be <backend address>"
                + PAWAPAY_CALLBACK_PATH + ". Neither can be checked from here.");

        boolean secretSet = isSet(flutterwaveSecretKey);
        Environment cardEnv = !secretSet ? Environment.UNKNOWN
                : flutterwaveSecretKey.contains("_TEST") ? Environment.SANDBOX : Environment.LIVE;
        if (!secretSet) problems.add("FLUTTERWAVE_SECRET_KEY is not set: card payments cannot start.");
        if (cardEnv == Environment.SANDBOX) {
            warnings.add("Card payments use a Flutterwave TEST key: no real money moves.");
        }
        if (!isSet(flutterwaveWebhookHash)) {
            problems.add("FLUTTERWAVE_WEBHOOK_SECRET_HASH is not set: every Flutterwave webhook will be refused.");
        }
        if (!isSet(flutterwaveRedirectUrl)) {
            problems.add("FLUTTERWAVE_REDIRECT_URL is not set: customers have nowhere to return after paying by card.");
        }

        return new SetupStatus(problems.isEmpty(),
                new MobileMoney(momoEnv, baseUrl, tokenSet, pawaPayProvider, PAWAPAY_CALLBACK_PATH),
                new Card(cardEnv, secretSet, isSet(flutterwaveWebhookHash), flutterwaveRedirectUrl,
                        FLUTTERWAVE_WEBHOOK_PATH),
                List.copyOf(problems), List.copyOf(warnings));
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        String t = url.trim();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }
}
