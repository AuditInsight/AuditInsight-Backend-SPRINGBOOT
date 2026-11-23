package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.PaymentProvider;
import com.diana.auditinsightbackendspringboot.Enum.PaymentStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Models.Payment;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.config.ServerTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One payment as the Super Admin sees it: who paid, for which organisation and plan, how, how much was expected and
 * actually received, the operator's transaction reference, why it failed, and when it was started and settled.
 * Times carry their time zone so the browser shows them in the viewer's local time.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminPaymentResponse {
    private UUID id;
    private UUID organisationId;
    private String organisationName;
    private SubscriptionType subscriptionType;
    private PaymentProvider provider;
    private PaymentStatus status;
    private BigDecimal expectedAmount;
    /** What the provider confirmed was collected; null unless money arrived (SUCCESSFUL or UNDERPAID). */
    private BigDecimal receivedAmount;
    private String currency;
    private Long payerUserId;
    private String payerName;
    private String payerEmail;
    /** The Mobile Money number that was charged (MoMo only). */
    private String payerPhone;
    /** MoMo: the operator's transaction id. Card: our Flutterwave reference. */
    private String providerReference;
    private String failureReason;
    /** The paid period this payment bought, once SUCCESSFUL. */
    private UUID subscriptionId;
    private OffsetDateTime createdAt;
    /** When the payment reached its final outcome; null while PENDING. */
    private OffsetDateTime resolvedAt;

    public static AdminPaymentResponse of(Payment payment, String organisationName, User payer) {
        AdminPaymentResponse r = new AdminPaymentResponse();
        r.setId(payment.getId());
        r.setOrganisationId(payment.getOrganisationId());
        r.setOrganisationName(organisationName);
        r.setSubscriptionType(payment.getSubscriptionType());
        r.setProvider(payment.getProvider());
        r.setStatus(payment.getStatus());
        r.setExpectedAmount(payment.getExpectedAmount());
        r.setReceivedAmount(payment.getReceivedAmount());
        r.setCurrency(payment.getCurrency());
        r.setPayerUserId(payment.getCreatedBy());
        if (payer != null) {
            r.setPayerName(payer.getFullName());
            r.setPayerEmail(payer.getUsername());
        }
        r.setPayerPhone(payment.getPayerPhone());
        r.setProviderReference(payment.getProviderTransactionId());
        r.setFailureReason(payment.getFailureReason());
        r.setSubscriptionId(payment.getSubscriptionId());
        r.setCreatedAt(ServerTime.withOffset(payment.getCreatedAt()));
        r.setResolvedAt(ServerTime.withOffset(payment.getResolvedAt()));
        return r;
    }
}
