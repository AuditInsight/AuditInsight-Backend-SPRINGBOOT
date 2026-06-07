package com.diana.auditinsightbackendspringboot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                                After Google authentication you will be redirected with a JWT token as a query \
                                parameter. Copy that token and paste it into the **Authorize** dialog above.
                                """))
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
