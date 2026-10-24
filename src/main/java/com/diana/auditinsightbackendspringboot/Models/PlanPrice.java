package com.diana.auditinsightbackendspringboot.Models;

import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("plan_prices")
@Getter
@Setter
public class PlanPrice {

    @Id
    @Column("subscription_type")
    private SubscriptionType subscriptionType;

    @Column("price")
    private BigDecimal price;

    @Column("currency")
    private String currency;

    @Column("duration_days")
    private int durationDays;

    @Column("updated_by")
    private Long updatedBy;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
