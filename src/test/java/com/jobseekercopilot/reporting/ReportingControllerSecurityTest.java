package com.jobseekercopilot.reporting;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobseekercopilot.reporting.controller.ReportingController;
import com.jobseekercopilot.reporting.dto.ApplicationSummary;
import com.jobseekercopilot.reporting.dto.ReportingSummaryResponse;
import com.jobseekercopilot.reporting.security.ReportingServiceAuthenticationFilter;
import com.jobseekercopilot.reporting.security.ReportingServiceCredentials;
import com.jobseekercopilot.reporting.service.ReportingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = ReportingController.class,
        properties = {
                "reporting.security.gateway-token="
                        + "test-only-reporting-gateway-token-32-bytes",
                "reporting.security.application-tracker-reader-token="
                        + "test-only-application-reader-token-32-bytes"
        })
@Import({
        ReportingServiceAuthenticationFilter.class,
        ReportingServiceCredentials.class
})
class ReportingControllerSecurityTest {
    private static final String GATEWAY_TOKEN =
            "test-only-reporting-gateway-token-32-bytes";

    @Autowired private MockMvc mockMvc;
    @MockBean private ReportingService reportingService;

    @Test
    void missingServiceIdentityFailsClosed() throws Exception {
        mockMvc.perform(get("/api/v1/reports/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void exactGatewayIdentityOwnerAndBearerReachService() throws Exception {
        when(reportingService.summary(eq("subject-123"), eq("access-token")))
                .thenReturn(new ReportingSummaryResponse(
                        "subject-123",
                        new ApplicationSummary(0, 1, 0, 0, 0, 0, 0, 1),
                        List.of(),
                        null,
                        "Applied"));

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("X-Service-Token", GATEWAY_TOKEN)
                        .header("X-Report-Owner", "subject-123")
                        .header("X-User-Id", "forged-user")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.userId").value("subject-123"))
                .andExpect(jsonPath("$.applicationSummary.applied").value(1));
    }

    @Test
    void evidenceExportRequiresGatewayIdentityAndUsesFixedDownloadHeaders() throws Exception {
        when(reportingService.evidenceExport(eq("subject-123"), eq("access-token")))
                .thenReturn("Persisted work-search evidence");

        mockMvc.perform(get("/api/v1/reports/evidence.txt")
                        .header("X-Service-Token", GATEWAY_TOKEN)
                        .header("X-Report-Owner", "subject-123")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=job-search-evidence.txt"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("Persisted work-search evidence"));
    }

    @Test
    void duplicateOrWrongGatewayIdentityFailsClosed() throws Exception {
        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("X-Service-Token", GATEWAY_TOKEN, GATEWAY_TOKEN)
                        .header("X-Report-Owner", "subject-123")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("X-Service-Token", "wrong-service-identity-value-32-bytes")
                        .header("X-Report-Owner", "subject-123")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void runtimeOpenApiIsDisabledByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}
