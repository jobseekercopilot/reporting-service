package com.jobseekercopilot.reporting.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI reportingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Jobseeker Copilot - Reporting Service API")
                .description("Produces reporting summaries, activity timelines, UC journal text, and commitment progress.")
                .version("1.0.0"));
    }
}
