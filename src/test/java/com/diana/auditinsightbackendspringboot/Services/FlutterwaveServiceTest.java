package com.diana.auditinsightbackendspringboot.Services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlutterwaveServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private FlutterwaveService flutterwaveService;

    @BeforeEach
    void setUp() {
        flutterwaveService = new FlutterwaveService();
        ReflectionTestUtils.setField(flutterwaveService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(flutterwaveService, "baseUrl", "https://api.flutterwave.com/v3");
        ReflectionTestUtils.setField(flutterwaveService, "secretKey", "test-secret");
        ReflectionTestUtils.setField(flutterwaveService, "webhookSecretHash", "expected-hash");
        ReflectionTestUtils.setField(flutterwaveService, "redirectUrl", "http://localhost:8080/callback");
    }

    @Test
    void isValidWebhookSignature_matchingHash_returnsTrue() {
        org.assertj.core.api.Assertions.assertThat(flutterwaveService.isValidWebhookSignature("expected-hash")).isTrue();
    }

    @Test
    void isValidWebhookSignature_mismatchedHash_returnsFalse() {
        org.assertj.core.api.Assertions.assertThat(flutterwaveService.isValidWebhookSignature("wrong-hash")).isFalse();
        org.assertj.core.api.Assertions.assertThat(flutterwaveService.isValidWebhookSignature(null)).isFalse();
    }

    @Test
    void initiateCheckout_success_returnsHostedLink() {
        when(restTemplate.exchange(contains("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "success",
                        "data", Map.of("link", "https://checkout.flutterwave.com/pay/xyz"))));

        StepVerifier.create(flutterwaveService.initiateCheckout("AI-1", new BigDecimal("29.00"), "USD", "a@b.com"))
                .expectNext("https://checkout.flutterwave.com/pay/xyz")
                .verifyComplete();
    }

    @Test
    void initiateCheckout_missingLink_propagatesError() {
        when(restTemplate.exchange(contains("/payments"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "success", "data", Map.of())));

        StepVerifier.create(flutterwaveService.initiateCheckout("AI-1", new BigDecimal("29.00"), "USD", "a@b.com"))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void verifyTransaction_successfulStatus_mapsToSuccessfulTrue() {
        when(restTemplate.exchange(contains("/transactions/999/verify"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "success",
                        "data", Map.of("status", "successful", "tx_ref", "AI-1",
                                "amount", "29.00", "currency", "USD"))));

        StepVerifier.create(flutterwaveService.verifyTransaction("999"))
                .expectNextMatches(r -> r.successful() && r.txRef().equals("AI-1"))
                .verifyComplete();
    }

    @Test
    void verifyTransaction_failedStatus_mapsToSuccessfulFalse() {
        when(restTemplate.exchange(contains("/transactions/999/verify"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "success",
                        "data", Map.of("status", "failed", "tx_ref", "AI-1"))));

        StepVerifier.create(flutterwaveService.verifyTransaction("999"))
                .expectNextMatches(r -> !r.successful())
                .verifyComplete();
    }

    @Test
    void verifyTransaction_apiCallThrows_propagatesError() {
        when(restTemplate.exchange(contains("/transactions/999/verify"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("network error"));

        StepVerifier.create(flutterwaveService.verifyTransaction("999"))
                .expectError(RuntimeException.class)
                .verify();
    }
}
