package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.Services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The plan catalog is public (the landing page shows live prices to visitors who are not logged in),
 * while changing prices stays admin-only. Runs against the real security filter chain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlanEndpointsSecurityTest {

    // Own H2 instance — see AdminEndpointsSecurityTest for why the shared "testdb" can't be reused.
    @DynamicPropertySource
    static void overrideDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url",
                () -> "r2dbc:h2:mem:///plan-endpoints-security-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE");
    }

    @LocalServerPort
    private int port;

    @MockitoBean
    private EmailService emailService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void listPlans_withoutLogin_returnsTheLivePrices() {
        webTestClient.get().uri("/api/plans")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[0].amount").exists();
    }

    @Test
    void adminPlanPrices_withoutLogin_stillUnauthorized() {
        webTestClient.get().uri("/api/admin/plans")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.put().uri("/api/admin/plans/MONTHLY")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"price\": 1}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void otherEndpoints_withoutLogin_stillUnauthorized() {
        webTestClient.get().uri("/api/organisations")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
