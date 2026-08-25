package com.roomsync.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("RoomSync Core Backend API")
                        .version("1.0.0")
                        .description("Enterprise Meeting Room Booking and Occupancy Management System featuring deterministic concurrency control, immutable audit trails, and transactional idempotency.")
                        .contact(new Contact().name("RoomSync Platform Team").email("platform@roomsync.com")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Provide the JWT access token in the format: Bearer <token>"))
                        .addParameters("Idempotency-Key", new HeaderParameter()
                                .name("Idempotency-Key")
                                .description("Unique client-generated idempotency key for safely retrying mutation requests without duplicate side-effects.")
                                .required(false))
                        .addParameters("X-Correlation-Id", new HeaderParameter()
                                .name("X-Correlation-Id")
                                .description("Distributed tracing correlation identifier.")
                                .required(false)));
    }
}
