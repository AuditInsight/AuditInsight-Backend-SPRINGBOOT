package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.DTOs.*;
import com.diana.auditinsightbackendspringboot.Exceptions.Custom.ForbiddenException;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Models.Subscription;
import com.diana.auditinsightbackendspringboot.Services.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/subscriptions")
@Tag(name = "Subscriptions", description = "Plan checkout via Mobile Money (pawaPay)/card and subscription status")
public class SubscriptionController {

    private final PaymentService paymentService;

    public SubscriptionController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{organisationId}/checkout/momo")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Start a MoMo checkout",
               description = "Creates a pending payment and initiates a Mobile Money deposit via pawaPay for the given plan.")
    public Mono<ResponseEntity<PaymentStatusResponse>> startMomoCheckout(
            Authentication auth,
            @PathVariable UUID organisationId,
            @Valid @RequestBody StartMomoCheckoutRequest request) {
        return paymentService.startMomoCheckout(organisationId, auth.getName(), request.getSubscriptionType(),
                        request.getPhoneNumber())
                .map(payment -> new ResponseEntity<>(toStatusResponse(payment), HttpStatus.CREATED));
    }

    @GetMapping("/payments/{paymentId}/status")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Check MoMo payment status",
               description = "Polls pawaPay for the current status of a pending Mobile Money payment and " +
                       "activates the subscription once it's confirmed successful.")
    public Mono<ResponseEntity<PaymentStatusResponse>> getMomoPaymentStatus(
            Authentication auth,
            @PathVariable UUID paymentId) {
        return paymentService.getMomoPaymentStatus(paymentId, auth.getName())
                .map(payment -> ResponseEntity.ok(toStatusResponse(payment)));
    }

    @PostMapping("/{organisationId}/checkout/card")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Start a card checkout",
               description = "Creates a pending payment and returns a Flutterwave hosted checkout URL.")
    public Mono<ResponseEntity<CardCheckoutResponse>> startCardCheckout(
            Authentication auth,
            @PathVariable UUID organisationId,
            @Valid @RequestBody StartCardCheckoutRequest request) {
        return paymentService.startCardCheckout(organisationId, auth.getName(), request.getSubscriptionType())
                .map(result -> {
                    CardCheckoutResponse response = new CardCheckoutResponse();
                    response.setPaymentId(result.payment().getId());
                    response.setCheckoutUrl(result.checkoutUrl());
                    return new ResponseEntity<>(response, HttpStatus.CREATED);
                });
    }

    @PostMapping("/webhooks/flutterwave")
    @Operation(summary = "Flutterwave webhook receiver",
               description = "Receives payment completion callbacks from Flutterwave. Verified via the " +
                       "'verif-hash' header rather than a bearer token.")
    public Mono<ResponseEntity<Void>> flutterwaveWebhook(
            @RequestHeader(value = "verif-hash", required = false) String verifHash,
            @RequestBody Map<String, Object> payload) {
        return paymentService.handleFlutterwaveWebhook(verifHash, payload)
                .thenReturn(ResponseEntity.ok().<Void>build())
                .onErrorResume(ForbiddenException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<Void>build()))
                .onErrorResume(e -> {
                    // Always acknowledge so Flutterwave doesn't retry-storm us; the failure is logged for follow-up.
                    log.error("Error processing Flutterwave webhook", e);
                    return Mono.just(ResponseEntity.ok().<Void>build());
                });
    }

    @PostMapping("/webhooks/pawapay")
    @Operation(summary = "pawaPay deposit callback receiver",
               description = "Receives MoMo deposit completion callbacks from pawaPay. The callback is only " +
                       "used as a trigger — the outcome is always re-verified against the pawaPay API " +
                       "before any subscription is activated. Existing status polling remains available " +
                       "as a backup, so this always acknowledges with 200 rather than retry-storming pawaPay.")
    public Mono<ResponseEntity<Void>> pawaPayWebhook(@RequestBody Map<String, Object> payload) {
        Object depositIdValue = payload.get("depositId");
        UUID depositId;
        try {
            depositId = UUID.fromString(String.valueOf(depositIdValue));
        } catch (IllegalArgumentException e) {
            log.warn("pawaPay webhook payload had a missing/malformed depositId: {}", depositIdValue);
            return Mono.just(ResponseEntity.ok().build());
        }

        return paymentService.handlePawaPayCallback(depositId)
                .thenReturn(ResponseEntity.ok().<Void>build())
                .onErrorResume(e -> {
                    log.error("Error processing pawaPay webhook for deposit {}", depositId, e);
                    return Mono.just(ResponseEntity.ok().<Void>build());
                });
    }

    @GetMapping("/{organisationId}/active")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get active subscription",
               description = "Returns the organisation's current active subscription, if any.")
    public Mono<ResponseEntity<SubscriptionResponse>> getActiveSubscription(
            Authentication auth,
            @PathVariable UUID organisationId) {
        return paymentService.getActiveSubscription(organisationId, auth.getName())
                .map(subscription -> ResponseEntity.ok(toResponse(subscription)));
    }

    private PaymentStatusResponse toStatusResponse(Payment payment) {
        PaymentStatusResponse r = new PaymentStatusResponse();
        r.setPaymentId(payment.getId());
        r.setProvider(payment.getProvider());
        r.setStatus(payment.getStatus());
        r.setExpectedAmount(payment.getExpectedAmount());
        r.setReceivedAmount(payment.getReceivedAmount());
        r.setCurrency(payment.getCurrency());
        r.setSubscriptionId(payment.getSubscriptionId());
        r.setFailureReason(payment.getFailureReason());
        return r;
    }

    private SubscriptionResponse toResponse(Subscription subscription) {
        SubscriptionResponse r = new SubscriptionResponse();
        r.setId(subscription.getId());
        r.setOrganisationId(subscription.getOrganisationId());
        r.setSubscriptionType(subscription.getSubscriptionType());
        r.setStatus(subscription.getStatus());
        r.setStartDate(subscription.getStartDate());
        r.setEndDate(subscription.getEndDate());
        return r;
    }
}
