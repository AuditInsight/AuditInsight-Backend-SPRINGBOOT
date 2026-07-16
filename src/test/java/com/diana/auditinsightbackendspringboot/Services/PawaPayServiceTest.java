package com.diana.auditinsightbackendspringboot.Services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PawaPayServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private PawaPayService pawaPayService;

    @BeforeEach
    void setUp() {
        pawaPayService = new PawaPayService();
        ReflectionTestUtils.setField(pawaPayService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(pawaPayService, "baseUrl", "https://api.sandbox.pawapay.io");
        ReflectionTestUtils.setField(pawaPayService, "apiToken", "test-pawapay-token");
        ReflectionTestUtils.setField(pawaPayService, "provider", "MTN_MOMO_RWA");
    }

    @Test
    void requestDeposit_accepted_postsDepositWithBearerTokenAndNormalizedPhone() {
        when(restTemplate.exchange(contains("/v2/deposits"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("depositId", "x", "status", "ACCEPTED")));

        UUID depositId = UUID.randomUUID();
        StepVerifier.create(pawaPayService.requestDeposit(depositId, new BigDecimal("14200"), "0788123456"))
                .verifyComplete();

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(contains("/v2/deposits"), eq(HttpMethod.POST),
                captor.capture(), eq(Map.class));

        HttpHeaders headers = captor.getValue().getHeaders();
        org.assertj.core.api.Assertions.assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer test-pawapay-token");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        org.assertj.core.api.Assertions.assertThat(body.get("depositId")).isEqualTo(depositId.toString());
        org.assertj.core.api.Assertions.assertThat(body.get("currency")).isEqualTo("RWF");

        @SuppressWarnings("unchecked")
        Map<String, Object> payer = (Map<String, Object>) body.get("payer");
        @SuppressWarnings("unchecked")
        Map<String, Object> accountDetails = (Map<String, Object>) payer.get("accountDetails");
        org.assertj.core.api.Assertions.assertThat(accountDetails.get("phoneNumber")).isEqualTo("250788123456");
        org.assertj.core.api.Assertions.assertThat(accountDetails.get("provider")).isEqualTo("MTN_MOMO_RWA");
    }

    @Test
    void requestDeposit_rejected_throws() {
        when(restTemplate.exchange(contains("/v2/deposits"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("depositId", "x", "status", "REJECTED",
                        "failureReason", Map.of("failureCode", "INVALID_PHONE_NUMBER"))));

        StepVerifier.create(pawaPayService.requestDeposit(UUID.randomUUID(), BigDecimal.TEN, "0788123456"))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void requestDeposit_nonSuccessHttpStatus_throws() {
        when(restTemplate.exchange(contains("/v2/deposits"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.status(500).build());

        StepVerifier.create(pawaPayService.requestDeposit(UUID.randomUUID(), BigDecimal.TEN, "0788123456"))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void getStatus_completed_mapsToSuccessful() {
        UUID depositId = UUID.randomUUID();
        when(restTemplate.exchange(contains("/v2/deposits/" + depositId), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "FOUND",
                        "data", Map.of("status", "COMPLETED"))));

        StepVerifier.create(pawaPayService.getStatus(depositId))
                .expectNext(PawaPayService.PawaPayStatus.SUCCESSFUL)
                .verifyComplete();
    }

    @Test
    void getStatus_failed_mapsToFailed() {
        UUID depositId = UUID.randomUUID();
        when(restTemplate.exchange(contains("/v2/deposits/" + depositId), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "FOUND",
                        "data", Map.of("status", "FAILED"))));

        StepVerifier.create(pawaPayService.getStatus(depositId))
                .expectNext(PawaPayService.PawaPayStatus.FAILED)
                .verifyComplete();
    }

    @Test
    void getStatus_processing_mapsToPending() {
        UUID depositId = UUID.randomUUID();
        when(restTemplate.exchange(contains("/v2/deposits/" + depositId), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "FOUND",
                        "data", Map.of("status", "PROCESSING"))));

        StepVerifier.create(pawaPayService.getStatus(depositId))
                .expectNext(PawaPayService.PawaPayStatus.PENDING)
                .verifyComplete();
    }

    @Test
    void getStatus_notFound_throws() {
        UUID depositId = UUID.randomUUID();
        when(restTemplate.exchange(contains("/v2/deposits/" + depositId), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "NOT_FOUND")));

        StepVerifier.create(pawaPayService.getStatus(depositId))
                .expectError(RuntimeException.class)
                .verify();
    }
}