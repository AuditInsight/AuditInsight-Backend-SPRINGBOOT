package com.diana.auditinsightbackendspringboot.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Thin client for Flutterwave's hosted checkout + transaction verification APIs.
 */
@Service
@Slf4j
public class FlutterwaveService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${flutterwave.api.base-url}")
    private String baseUrl;

    @Value("${flutterwave.secret-key}")
    private String secretKey;

    @Value("${flutterwave.webhook-secret-hash}")
    private String webhookSecretHash;

    @Value("${flutterwave.redirect-url}")
    private String redirectUrl;

    public record VerificationResult(boolean successful, String status, String txRef,
                                      BigDecimal amount, String currency) {}

    public boolean isValidWebhookSignature(String verifHashHeader) {
        return webhookSecretHash != null && !webhookSecretHash.isBlank() && webhookSecretHash.equals(verifHashHeader);
    }

    public Mono<String> initiateCheckout(String txRef, BigDecimal amount, String currency, String customerEmail) {
        return Mono.fromCallable(() -> doInitiateCheckout(txRef, amount, currency, customerEmail))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<VerificationResult> verifyTransaction(String transactionId) {
        return Mono.fromCallable(() -> doVerifyTransaction(transactionId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private String doInitiateCheckout(String txRef, BigDecimal amount, String currency, String customerEmail) {
        Map<String, Object> body = new HashMap<>();
        body.put("tx_ref", txRef);
        body.put("amount", amount.toPlainString());
        body.put("currency", currency);
        body.put("redirect_url", redirectUrl);
        body.put("customer", Map.of("email", customerEmail));

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(baseUrl + "/payments", HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()), Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initiate Flutterwave checkout: " + e.getMessage(), e);
        }

        Map<String, Object> responseBody = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || responseBody == null) {
            throw new RuntimeException("Flutterwave checkout initiation returned HTTP " + response.getStatusCode());
        }
        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
        if (data == null || data.get("link") == null) {
            throw new RuntimeException("Flutterwave checkout response missing link");
        }
        return String.valueOf(data.get("link"));
    }

    @SuppressWarnings("unchecked")
    private VerificationResult doVerifyTransaction(String transactionId) {
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(baseUrl + "/transactions/" + transactionId + "/verify",
                    HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify Flutterwave transaction: " + e.getMessage(), e);
        }

        Map<String, Object> responseBody = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || responseBody == null) {
            throw new RuntimeException("Flutterwave verify returned HTTP " + response.getStatusCode());
        }
        Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
        if (data == null) {
            throw new RuntimeException("Flutterwave verify response missing data");
        }

        String status = String.valueOf(data.get("status"));
        boolean successful = "successful".equalsIgnoreCase(status);
        BigDecimal amount = data.get("amount") != null ? new BigDecimal(data.get("amount").toString()) : null;
        String currency = data.get("currency") != null ? String.valueOf(data.get("currency")) : null;
        String txRef = data.get("tx_ref") != null ? String.valueOf(data.get("tx_ref")) : null;
        return new VerificationResult(successful, status, txRef, amount, currency);
    }
}
