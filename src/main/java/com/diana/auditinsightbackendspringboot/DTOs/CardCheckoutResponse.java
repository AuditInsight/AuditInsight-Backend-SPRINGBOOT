package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CardCheckoutResponse {
    private UUID paymentId;
    private String checkoutUrl;
}
