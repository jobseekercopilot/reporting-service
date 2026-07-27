package com.jobseekercopilot.reporting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "reporting.security.gateway-token="
                + "test-only-reporting-gateway-token-32-bytes",
        "reporting.security.application-tracker-reader-token="
                + "test-only-application-reader-token-32-bytes",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
class OpenApiExportTest {
    private static final Path CONTRACT = Path.of("contracts/openapi.json");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void publishedContractMatchesTheRunningApplication() throws Exception {
        String spec = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var generated = objectMapper.readTree(spec);
        String formatted = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(generated)
                + System.lineSeparator();
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi.json"), formatted);

        if (Boolean.getBoolean("reportingService.updateContract")) {
            Files.writeString(CONTRACT, formatted);
        } else {
            org.junit.jupiter.api.Assertions.assertEquals(
                    objectMapper.readTree(Files.readString(CONTRACT)),
                    generated,
                    "Published OpenAPI contract is stale; update and review the tracked contract");
        }
    }
}
