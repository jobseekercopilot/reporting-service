package com.jobseekercopilot.reporting.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI reportingOpenApi() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(
                                "gatewayServiceToken",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Service-Token"))
                        .addSecuritySchemes(
                                "userBearer",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("gatewayServiceToken")
                        .addList("userBearer"))
                .info(new Info()
                        .title("Jobseeker Copilot - Reporting Service API")
                        .description("Produces owner-scoped reporting summaries, content-free application/document activity timelines, UC journal text, and commitment progress. Unknown activity fields and event types are never copied into claimant evidence.")
                        .version("2.1.0"));
    }
}
