package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.OrganisationType;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionStatus;
import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminOrganisationResponse {
    private UUID id;
    private String name;
    private OrganisationType industry;
    private String countryCode;
    private String defaultCurrency;
    private LocalDateTime createdAt;
    private long memberCount;
    private SubscriptionStatus subscriptionStatus;
    private SubscriptionType subscriptionType;
    private LocalDateTime subscriptionEndDate;
}
