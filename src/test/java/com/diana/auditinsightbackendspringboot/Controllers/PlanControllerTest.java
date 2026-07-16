package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.Enum.PlanTier;
import com.diana.auditinsightbackendspringboot.Exceptions.Global.GlobalExceptionHandler;
import com.diana.auditinsightbackendspringboot.Models.Plan;
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

    @Test
    void list_returnsAllPlansWithPricingAndFeatures() {
        Plan free = new Plan();
        free.setTier(PlanTier.FREE);
        free.setDescription("Get started with basic audit management");
        free.setMonthlyPriceUsd(BigDecimal.ZERO);
        free.setYearlyPriceUsd(BigDecimal.ZERO);
        free.setMaxUsers(2);
        free.setAuditsPerMonth(5);
        free.setStorageGb(1);
        free.setBasicReports(true);
        free.setEmailSupport(true);

        Plan enterprise = new Plan();
        enterprise.setTier(PlanTier.ENTERPRISE);
        enterprise.setDescription("Enterprise-grade security and compliance");
        enterprise.setMonthlyPriceUsd(new BigDecimal("199"));
        enterprise.setYearlyPriceUsd(new BigDecimal("1990"));
        enterprise.setSlaGuarantee(true);

        when(planService.listPlans()).thenReturn(Flux.just(free, enterprise));

        webTestClient.get().uri("/api/plans")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class)
                .hasSize(2);
    }
}
