package com.jobseekercopilot.reporting.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReportingServiceCredentials {
    static final int MINIMUM_TOKEN_BYTES = 32;

    private final String gatewayToken;
    private final String applicationTrackerReaderToken;

    public ReportingServiceCredentials(
            @Value("${reporting.security.gateway-token}") String gatewayToken,
            @Value("${reporting.security.application-tracker-reader-token}")
            String applicationTrackerReaderToken) {
        this.gatewayToken = validate(gatewayToken, "Reporting Gateway token");
        this.applicationTrackerReaderToken = validate(
                applicationTrackerReaderToken,
                "Application Tracker reader token");
        if (MessageDigest.isEqual(
                this.gatewayToken.getBytes(StandardCharsets.UTF_8),
                this.applicationTrackerReaderToken.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException(
                    "Reporting service identities must be distinct.");
        }
    }

    public String gatewayToken() {
        return gatewayToken;
    }

    public String applicationTrackerReaderToken() {
        return applicationTrackerReaderToken;
    }

    private static String validate(String value, String label) {
        if (value == null
                || value.isBlank()
                || value.getBytes(StandardCharsets.UTF_8).length
                < MINIMUM_TOKEN_BYTES) {
            throw new IllegalStateException(
                    label + " must contain at least 32 bytes.");
        }
        return value;
    }
}
