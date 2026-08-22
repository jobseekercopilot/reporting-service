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
    private final String documentStoreReaderToken;

    public ReportingServiceCredentials(
            @Value("${reporting.security.gateway-token}") String gatewayToken,
            @Value("${reporting.security.application-tracker-reader-token}")
            String applicationTrackerReaderToken,
            @Value("${reporting.security.document-store-reader-token}")
            String documentStoreReaderToken) {
        this.gatewayToken = validate(gatewayToken, "Reporting Gateway token");
        this.applicationTrackerReaderToken = validate(
                applicationTrackerReaderToken,
                "Application Tracker reader token");
        this.documentStoreReaderToken = validate(
                documentStoreReaderToken,
                "Document Store reader token");
        requireDistinct(this.gatewayToken, this.applicationTrackerReaderToken);
        requireDistinct(this.gatewayToken, this.documentStoreReaderToken);
        requireDistinct(
                this.applicationTrackerReaderToken,
                this.documentStoreReaderToken);
    }

    public String gatewayToken() {
        return gatewayToken;
    }

    public String applicationTrackerReaderToken() {
        return applicationTrackerReaderToken;
    }

    public String documentStoreReaderToken() {
        return documentStoreReaderToken;
    }

    private static void requireDistinct(String first, String second) {
        if (MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException(
                    "Reporting service identities must be distinct.");
        }
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
