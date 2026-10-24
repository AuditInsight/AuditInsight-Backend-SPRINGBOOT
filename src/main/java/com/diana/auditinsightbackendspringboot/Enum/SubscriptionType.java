package com.diana.auditinsightbackendspringboot.Enum;

import java.math.BigDecimal;

/**
 * The three fixed-price subscription periods. Prices are backend-controlled and fixed — the
 * frontend never supplies an amount, it only selects one of these codes.
 */
public enum SubscriptionType {

    MONTHLY(30, new BigDecimal("15000")),
    SIX_MONTHS(180, new BigDecimal("80000")),
    ANNUAL(365, new BigDecimal("150000"));

    public static final String CURRENCY = "RWF";

    private final int durationDays;
    private final BigDecimal priceRwf;

    SubscriptionType(int durationDays, BigDecimal priceRwf) {
        this.durationDays = durationDays;
        this.priceRwf = priceRwf;
    }

    /** Original/reference value — live duration is platform-admin-editable via {@code plan_prices}. */
    public int getDurationDays() {
        return durationDays;
    }

    /** Original/reference value — live price is platform-admin-editable via {@code plan_prices}. */
    public BigDecimal getPriceRwf() {
        return priceRwf;
    }
}
