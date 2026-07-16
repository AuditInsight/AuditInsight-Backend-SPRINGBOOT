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

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${pawapay.api.base-url}")
    private String baseUrl;

    @Value("${pawapay.api.token}")
    private String apiToken;

    @Value("${pawapay.provider}")
    private String provider;

    public enum PawaPayStatus { PENDING, SUCCESSFUL, FAILED }

    public Mono<Void> requestDeposit(UUID depositId, BigDecimal amountRwf, String phoneNumber) {
        return Mono.fromRunnable(() -> doRequestDeposit(depositId, amountRwf, phoneNumber))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<PawaPayStatus> getStatus(UUID depositId) {
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
        } catch (Exception e) {
            throw new RuntimeException("pawaPay deposit request failed: " + e.getMessage(), e);
        }

        Map<String, Object> responseBody = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || responseBody == null) {
            throw new RuntimeException("pawaPay deposit request returned HTTP " + response.getStatusCode());
        }

        String status = String.valueOf(responseBody.get("status"));
        if (!"ACCEPTED".equals(status) && !"DUPLICATE_IGNORED".equals(status)) {
            Object failure = responseBody.get("failureReason");
            throw new RuntimeException("pawaPay deposit was rejected: " + status
                    + (failure != null ? " (" + failure + ")" : ""));
        }
    }

    @SuppressWarnings("unchecked")
    private PawaPayStatus doGetStatus(UUID depositId) {
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
            throw new RuntimeException("pawaPay has no record of deposit " + depositId);
        }

        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
        if (data == null) {
            throw new RuntimeException("pawaPay status response missing data");
        }

        return switch (String.valueOf(data.get("status"))) {
            case "COMPLETED" -> PawaPayStatus.SUCCESSFUL;
            case "FAILED" -> PawaPayStatus.FAILED;
            default -> PawaPayStatus.PENDING; // ACCEPTED, PROCESSING, IN_RECONCILIATION
        };
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