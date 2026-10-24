package com.diana.auditinsightbackendspringboot.Admin;

import com.diana.auditinsightbackendspringboot.Authentication.JwtUtil;
import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Proves the "non-admins get 403" acceptance criterion end-to-end for every new /api/admin/**
 * endpoint. Needs a real running server + the real security filter chain (gateway path-matcher +
 * method-level @PreAuthorize) — unlike this repo's other controller tests, which bind straight to
 * the controller via WebTestClient.bindToController(...) and never load security at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminEndpointsSecurityTest {

    // A distinct named H2 instance — the shared "testdb" used by the rest of the "test" profile
    // stays alive for the whole JVM fork (DB_CLOSE_DELAY=-1), so reusing it here would make this
    // class's own schema-test.sql run re-insert the same plan_prices rows and collide on the PK.
    @DynamicPropertySource
    static void overrideDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url",
                () -> "r2dbc:h2:mem:///admin-endpoints-security-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE");
    }

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private DatabaseClient databaseClient;

    @MockitoBean
    private EmailService emailService;

    private String adminToken;
    private String clientToken;
    private String auditorToken;
    private String memberToken;
    private UUID organisationId;
    private String inactiveUsername;
    private String activeTargetUsername;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        String adminUsername = "admin-" + suffix + "@test.com";
        String clientUsername = "client-" + suffix + "@test.com";
        String auditorUsername = "auditor-" + suffix + "@test.com";
        String memberUsername = "member-" + suffix + "@test.com";
        inactiveUsername = "inactive-" + suffix + "@test.com";
        activeTargetUsername = "active-target-" + suffix + "@test.com";

        seedUser(adminUsername, Role.ADMIN, true);
        seedUser(clientUsername, Role.CLIENT, true);
        seedUser(auditorUsername, Role.AUDITOR, true);
        seedUser(memberUsername, Role.MEMBER, true);
        seedUser(inactiveUsername, Role.CLIENT, false);
        seedUser(activeTargetUsername, Role.CLIENT, true);

        adminToken = jwtUtil.generateToken(adminUsername, Role.ADMIN.name());
        clientToken = jwtUtil.generateToken(clientUsername, Role.CLIENT.name());
        auditorToken = jwtUtil.generateToken(auditorUsername, Role.AUDITOR.name());
        memberToken = jwtUtil.generateToken(memberUsername, Role.MEMBER.name());

        organisationId = UUID.randomUUID();
        databaseClient.sql("""
                        INSERT INTO organisation (id, client_id, name, country_code, default_currency, created_at)
                        VALUES (:id, :clientId, :name, 'RW', 'RWF', CURRENT_TIMESTAMP)
                        """)
                .bind("id", organisationId)
                .bind("clientId", UUID.randomUUID())
                .bind("name", "Security Test Org " + suffix)
                .fetch().rowsUpdated().block();
    }

    /**
     * Inserts via raw SQL rather than UserRepository.save() — the H2 test dialect generates an
     * uppercase column name for User's unannotated fields (password/role/verified/active), which
     * doesn't match schema-test.sql's lowercase columns. Unrelated to this test; real Postgres is
     * unaffected, so it's not worth changing the entity just for this.
     */
    private void seedUser(String username, Role role, boolean active) {
        databaseClient.sql("""
                        INSERT INTO users (username, password, full_name, role, auth_provider, verified, active, must_change_password, created_at)
                        VALUES (:username, :password, :fullName, :role, :authProvider, :verified, :active, :mustChangePassword, CURRENT_TIMESTAMP)
                        """)
                .bind("username", username)
                .bind("password", "$2a$10$abcdefghijklmnopqrstuvwxyz012345678901234567890123456")
                .bind("fullName", username)
                .bind("role", role.name())
                .bind("authProvider", "LOCAL")
                .bind("verified", true)
                .bind("active", active)
                .bind("mustChangePassword", false)
                .fetch().rowsUpdated().block();
    }

    private WebTestClient.ResponseSpec get(String token, String path) {
        return webTestClient.get().uri(path).header("Authorization", "Bearer " + token).exchange();
    }

    private WebTestClient.ResponseSpec patchNoBody(String token, String path) {
        return webTestClient.patch().uri(path).header("Authorization", "Bearer " + token).exchange();
    }

    private WebTestClient.ResponseSpec patch(String token, String path, Object body) {
        return webTestClient.patch().uri(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    private WebTestClient.ResponseSpec put(String token, String path, Object body) {
        return webTestClient.put().uri(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    private void assertForbiddenForNonAdmins(Function<String, WebTestClient.ResponseSpec> request) {
        request.apply(clientToken).expectStatus().isForbidden();
        request.apply(auditorToken).expectStatus().isForbidden();
        request.apply(memberToken).expectStatus().isForbidden();
    }

    @Test
    void listUsers_onlyAdminAllowed() {
        assertForbiddenForNonAdmins(token -> get(token, "/api/admin/users"));
        get(adminToken, "/api/admin/users").expectStatus().isOk();
    }

    @Test
    void activateAccount_onlyAdminAllowed() {
        String path = "/api/admin/users/" + inactiveUsername + "/activate";
        assertForbiddenForNonAdmins(token -> patchNoBody(token, path));
        patchNoBody(adminToken, path).expectStatus().isOk();
    }

    @Test
    void deactivateAccount_onlyAdminAllowed() {
        String path = "/api/admin/users/" + activeTargetUsername + "/deactivate";
        assertForbiddenForNonAdmins(token -> patchNoBody(token, path));
        patchNoBody(adminToken, path).expectStatus().isOk();
    }

    @Test
    void listOrganisations_onlyAdminAllowed() {
        assertForbiddenForNonAdmins(token -> get(token, "/api/admin/organisations"));
        get(adminToken, "/api/admin/organisations").expectStatus().isOk();
    }

    @Test
    void getOrganisationDetail_onlyAdminAllowed() {
        String path = "/api/admin/organisations/" + organisationId;
        assertForbiddenForNonAdmins(token -> get(token, path));
        get(adminToken, path).expectStatus().isOk();
    }

    @Test
    void changeSubscription_onlyAdminAllowed() {
        String path = "/api/admin/organisations/" + organisationId + "/subscription";
        Map<String, Object> body = Map.of("subscriptionType", "MONTHLY");
        assertForbiddenForNonAdmins(token -> patch(token, path, body));
        patch(adminToken, path, body).expectStatus().isOk();
    }

    @Test
    void listSubscriptions_onlyAdminAllowed() {
        assertForbiddenForNonAdmins(token -> get(token, "/api/admin/subscriptions"));
        get(adminToken, "/api/admin/subscriptions").expectStatus().isOk();
    }

    @Test
    void listPlans_onlyAdminAllowed() {
        assertForbiddenForNonAdmins(token -> get(token, "/api/admin/plans"));
        get(adminToken, "/api/admin/plans").expectStatus().isOk();
    }

    @Test
    void updatePlanPrice_onlyAdminAllowed() {
        String path = "/api/admin/plans/MONTHLY";
        Map<String, Object> body = Map.of("price", 15000, "durationDays", 30);
        assertForbiddenForNonAdmins(token -> put(token, path, body));
        put(adminToken, path, body).expectStatus().isOk();
    }

    @Test
    void listAuditLog_onlyAdminAllowed() {
        assertForbiddenForNonAdmins(token -> get(token, "/api/admin/audit-log"));
        get(adminToken, "/api/admin/audit-log").expectStatus().isOk();
    }
}
