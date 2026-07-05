package com.diana.auditinsightbackendspringboot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AuditInsight API")
                        .version("1.0.0")
                        .description("""
                                Secure, enterprise-grade auditing and compliance platform for real-time visibility \
                                into financial data, system logs, and user activity.

                                **Google OAuth2 login (CLIENT role only):**
                                Navigate to `/api/auth/social-login/google` in your browser. \
                                After Google authentication succeeds you'll be signed in via a browser session \
                                (no token needed for that session). To call protected endpoints from Swagger's \
                                "Try it out" instead, register/login normally and paste the returned JWT into \
                                the **Authorize** dialog above.
                                """))
                .servers(List.of(
                        new Server().url("https://auditinsight-backend-springboot-production.up.railway.app")
                                .description("Production "),
                        new Server().url("http://localhost:8080")
                                .description("Development")
                ))
                .externalDocs(new ExternalDocumentation()
                        .description("Sign in with Google")
                        .url("/api/auth/social-login/google"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                )
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
