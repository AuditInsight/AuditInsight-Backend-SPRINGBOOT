package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.Enum.SubscriptionType;
import com.diana.auditinsightbackendspringboot.Exceptions.Global.GlobalExceptionHandler;
import com.diana.auditinsightbackendspringboot.Models.PlanPrice;
import com.diana.auditinsightbackendspringboot.Services.PlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanControllerTest {

    @Mock
    private PlanService planService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient
                .bindToController(new PlanController(planService))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PlanPrice planPrice(SubscriptionType type, String price, int durationDays) {
        PlanPrice p = new PlanPrice();
        p.setSubscriptionType(type);
        p.setPrice(new BigDecimal(price));
        p.setCurrency("RWF");
        p.setDurationDays(durationDays);
        return p;
    }

    @Test
    void list_returnsAllThreeFixedPriceSubscriptionPeriods() {
        when(planService.listPlans()).thenReturn(Flux.just(
                planPrice(SubscriptionType.MONTHLY, "15000", 30),
                planPrice(SubscriptionType.SIX_MONTHS, "80000", 180),
                planPrice(SubscriptionType.ANNUAL, "150000", 365)));

        webTestClient.get().uri("/api/plans")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class)
                .hasSize(3);
    }
}
