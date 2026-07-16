package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Exceptions.Global.GlobalExceptionHandler;
import com.diana.auditinsightbackendspringboot.Services.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Only the Flutterwave webhook endpoint is exercised here — the other SubscriptionController
 * endpoints require an {@code Authentication} argument, which (as with the rest of this
 * codebase's controllers, e.g. TransactionController) isn't covered by a standalone
 * WebTestClient test; their behavior is covered at the PaymentService level instead.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private PaymentService paymentService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient
                .bindToController(new SubscriptionController(paymentService))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void flutterwaveWebhook_validSignature_returns200() {
        when(paymentService.handleFlutterwaveWebhook(anyString(), any())).thenReturn(Mono.empty());

        webTestClient.post().uri("/api/subscriptions/webhooks/flutterwave")
                .header("verif-hash", "good-hash")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "event": "charge.completed",
                          "data": { "id": "999", "tx_ref": "AI-1", "status": "successful" }
                        }""")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void flutterwaveWebhook_invalidSignature_returns401() {
        when(paymentService.handleFlutterwaveWebhook(anyString(), any()))
                .thenReturn(Mono.error(new ForbiddenException("Invalid webhook signature")));

        webTestClient.post().uri("/api/subscriptions/webhooks/flutterwave")
                .header("verif-hash", "bad-hash")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "event": "charge.completed",
                          "data": { "id": "999", "tx_ref": "AI-1", "status": "successful" }
                        }""")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void flutterwaveWebhook_internalProcessingError_stillReturns200() {
        when(paymentService.handleFlutterwaveWebhook(anyString(), any()))
                .thenReturn(Mono.error(new RuntimeException("Flutterwave verify API unreachable")));

        webTestClient.post().uri("/api/subscriptions/webhooks/flutterwave")
                .header("verif-hash", "good-hash")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "event": "charge.completed",
                          "data": { "id": "999", "tx_ref": "AI-1", "status": "successful" }
                        }""")
                .exchange()
                .expectStatus().isOk();
    }
}
