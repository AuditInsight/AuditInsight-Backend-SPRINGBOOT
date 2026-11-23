package com.diana.auditinsightbackendspringboot.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Thin client for the pawaPay Merchant API (v2, sandbox) — a mobile money aggregator that
 * fronts MTN MoMo/Airtel Money behind a single API. Unlike MTN's direct Collections API,
 * pawaPay uses a static bearer token (configured in its dashboard) rather than an OAuth
 * token exchange, so there is no token-fetch/cache step here.
 */
@Service
@Slf4j
public class PawaPayService {

    private final RestTemplate restTemplate = buildRestTemplate();

    /** Bounded waits: a hung pawaPay call must not hold a checkout (or the reconciliation sweep) open forever. */
    private static RestTemplate buildRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        return new RestTemplate(factory);
    }

    @Value("${pawapay.api.base-url}")
    private String baseUrl;

    @Value("${pawapay.api.token}")
    private String apiToken;

    @Value("${pawapay.provider}")
    private String provider;

    /** NOT_FOUND: pawaPay has no record of the deposit (the request never reached it). */
    public enum PawaPayStatus { PENDING, SUCCESSFUL, FAILED, NOT_FOUND }

    /** {@code providerReference} is the mobile money operator's own transaction id, when pawaPay reports it. */
    public record StatusResult(PawaPayStatus status, String failureReason, BigDecimal amount, String providerReference) {
        public StatusResult(PawaPayStatus status, String failureReason, BigDecimal amount) {
            this(status, failureReason, amount, null);
        }
    }

    /**
     * pawaPay definitely did not accept the deposit (it answered with a rejection or a 4xx), so no money can move.
     * Any other failure (timeout, connection error, 5xx) leaves the outcome unknown: the deposit may have been
     * created, so the payment must stay PENDING and be checked again rather than marked failed.
     */
    public static class DepositRejectedException extends RuntimeException {
        /** pawaPay's failureCode (e.g. INVALID_PHONE_NUMBER, AUTHENTICATION_ERROR), when it gave one. */
        private final String failureCode;

        public DepositRejectedException(String message, Throwable cause) {
            this(message, null, cause);
        }

        public DepositRejectedException(String message, String failureCode, Throwable cause) {
            super(message, cause);
            this.failureCode = failureCode;
        }

        public String getFailureCode() {
            return failureCode;
        }

        /** The rejection is our setup's fault (token, account, request format), not the customer's. */
        public boolean isConfigurationProblem() {
            return failureCode != null && CONFIGURATION_FAILURE_CODES.contains(failureCode);
        }
    }

    /** Rejections only a fix on our side (Railway settings or the pawaPay account) can cure. */
    static final java.util.Set<String> CONFIGURATION_FAILURE_CODES = java.util.Set.of(
            "AUTHENTICATION_ERROR", "AUTHORISATION_ERROR", "INVALID_PROVIDER", "INVALID_CURRENCY",
            "DEPOSITS_NOT_ALLOWED", "INVALID_INPUT", "MISSING_PARAMETER", "UNSUPPORTED_PARAMETER",
            "INVALID_PARAMETER", "INVALID_AMOUNT");

    public Mono<Void> requestDeposit(UUID depositId, BigDecimal amountRwf, String phoneNumber) {
        return Mono.fromRunnable(() -> doRequestDeposit(depositId, amountRwf, phoneNumber))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<StatusResult> getStatus(UUID depositId) {
        return Mono.fromCallable(() -> doGetStatus(depositId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private void doRequestDeposit(UUID depositId, BigDecimal amountRwf, String phoneNumber) {
        Map<String, Object> body = Map.of(
                "depositId", depositId.toString(),
                "payer", Map.of(
                        "type", "MMO",
                        "accountDetails", Map.of(
                                "phoneNumber", normalizePhoneNumber(phoneNumber),
                                "provider", provider)),
                "amount", amountRwf.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                "currency", "RWF",
                "customerMessage", "AuditInsight");

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(baseUrl + "/v2/deposits", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()), Map.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw rejected("pawaPay rejected the deposit request: " + e.getStatusCode(), errorBody(e),
                    e.getStatusCode().value(), e);
        } catch (Exception e) {
            throw new RuntimeException("pawaPay deposit request failed: " + e.getMessage(), e);
        }

        Map<String, Object> responseBody = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || responseBody == null) {
            throw new RuntimeException("pawaPay deposit request returned HTTP " + response.getStatusCode());
        }

        String status = String.valueOf(responseBody.get("status"));
        if (!"ACCEPTED".equals(status) && !"DUPLICATE_IGNORED".equals(status)) {
            throw rejected("pawaPay deposit was rejected: " + status, responseBody, 200, null);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> errorBody(org.springframework.web.client.HttpClientErrorException e) {
        try {
            return e.getResponseBodyAs(Map.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Builds the rejection with pawaPay's failureCode (falling back to the HTTP status for 401/403) and logs setup
     * problems as errors: a wrong token or account setting fails every payment, so it must be noticed at once.
     */
    @SuppressWarnings("unchecked")
    static DepositRejectedException rejected(String summary, Map<String, Object> body, int httpStatus, Throwable cause) {
        String code = null;
        String detail = null;
        Object failure = body == null ? null : body.get("failureReason");
        if (failure instanceof Map) {
            Map<String, Object> f = (Map<String, Object>) failure;
            code = f.get("failureCode") == null ? null : String.valueOf(f.get("failureCode"));
            detail = f.get("failureMessage") == null ? null : String.valueOf(f.get("failureMessage"));
        }
        if (code == null && httpStatus == 401) code = "AUTHENTICATION_ERROR";
        if (code == null && httpStatus == 403) code = "AUTHORISATION_ERROR";
        String message = summary + (code != null ? " (" + code + (detail != null ? ": " + detail : "") + ")" : "");
        DepositRejectedException e = new DepositRejectedException(message, code, cause);
        if (e.isConfigurationProblem()) {
            log.error("pawaPay SETUP PROBLEM — every Mobile Money payment will fail until this is fixed. Check "
                    + "PAWAPAY_API_TOKEN / PAWAPAY_API_BASE_URL on the server and the pawaPay account: {}", message);
        }
        return e;
    }

    @SuppressWarnings("unchecked")
    private StatusResult doGetStatus(UUID depositId) {
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(baseUrl + "/v2/deposits/" + depositId, HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), Map.class);
        } catch (Exception e) {
            throw new RuntimeException("pawaPay status check failed: " + e.getMessage(), e);
        }

        Map<String, Object> responseBody = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || responseBody == null) {
            throw new RuntimeException("pawaPay status check returned HTTP " + response.getStatusCode());
        }
        if ("NOT_FOUND".equals(responseBody.get("status"))) {
            return new StatusResult(PawaPayStatus.NOT_FOUND, "pawaPay has no record of this payment request", null);
        }

        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
        if (data == null) {
            throw new RuntimeException("pawaPay status response missing data");
        }

        BigDecimal amount = data.get("amount") != null ? new BigDecimal(String.valueOf(data.get("amount"))) : null;
        return switch (String.valueOf(data.get("status"))) {
            case "COMPLETED" -> new StatusResult(PawaPayStatus.SUCCESSFUL, null, amount, providerReference(data));
            case "FAILED" -> new StatusResult(PawaPayStatus.FAILED, extractFailureReason(data), amount);
            default -> new StatusResult(PawaPayStatus.PENDING, null, amount); // ACCEPTED, PROCESSING, IN_RECONCILIATION
        };
    }

    private static String providerReference(Map<String, Object> data) {
        Object ref = data.get("providerTransactionId");
        return ref == null ? null : String.valueOf(ref);
    }

    /**
     * pawaPay reports failures as a nested {failureCode, failureMessage} object on the deposit;
     * older/edge responses may send a plain string instead, so handle both defensively.
     */
    @SuppressWarnings("unchecked")
    private String extractFailureReason(Map<String, Object> data) {
        Object failure = data.get("failureReason");
        if (failure instanceof Map) {
            Map<String, Object> failureMap = (Map<String, Object>) failure;
            Object code = failureMap.get("failureCode");
            Object message = failureMap.get("failureMessage");
            if (code != null || message != null) {
                return String.valueOf(code) + (message != null ? ": " + message : "");
            }
        }
        return failure != null ? String.valueOf(failure) : "Unknown reason (pawaPay did not report a failure cause)";
    }

    /**
     * pawaPay requires the MSISDN with country code and no leading zero (e.g. 250788123456).
     * Local-format Rwandan numbers (0788123456) are the common input from the client app.
     */
    private String normalizePhoneNumber(String phoneNumber) {
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) {
            return "250" + digits.substring(1);
        }
        return digits;
    }
}