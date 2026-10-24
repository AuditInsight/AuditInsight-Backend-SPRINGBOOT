package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.OrganisationType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminOrganisationDetailResponse {
    private UUID id;
    private String name;
    private OrganisationType industry;
    private String countryCode;
    private String defaultCurrency;
    private LocalDateTime createdAt;
    private List<AdminMemberResponse> members;
    private List<AdminSubscriptionSummary> subscriptionHistory;
    private List<AdminPaymentResponse> payments;
}
