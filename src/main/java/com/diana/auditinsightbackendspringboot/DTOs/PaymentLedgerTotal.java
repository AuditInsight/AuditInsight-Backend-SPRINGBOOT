package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** One row of the ledger summary: how many payments have this status and currency, and how much money they brought in. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLedgerTotal {
    private String status;
    private String currency;
    private Long payments;
    /** Sum of the amounts providers confirmed were collected (0 when none arrived). */
    private BigDecimal received;
}
