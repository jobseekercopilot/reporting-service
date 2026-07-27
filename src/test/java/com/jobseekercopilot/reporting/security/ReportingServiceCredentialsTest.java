package com.jobseekercopilot.reporting.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReportingServiceCredentialsTest {
    private static final String GATEWAY_TOKEN =
            "test-only-reporting-gateway-token-32-bytes";
    private static final String READER_TOKEN =
            "test-only-application-reader-token-32-bytes";

    @Test
    void acceptsDistinctStrongRuntimeCredentials() {
        ReportingServiceCredentials credentials =
                new ReportingServiceCredentials(GATEWAY_TOKEN, READER_TOKEN);

        assertEquals(GATEWAY_TOKEN, credentials.gatewayToken());
        assertEquals(READER_TOKEN, credentials.applicationTrackerReaderToken());
    }

    @Test
    void rejectsMissingShortOrSharedCredentialsWithoutReflectingThem() {
        IllegalStateException missing = assertThrows(
                IllegalStateException.class,
                () -> new ReportingServiceCredentials("", READER_TOKEN));
        IllegalStateException shortToken = assertThrows(
                IllegalStateException.class,
                () -> new ReportingServiceCredentials("short", READER_TOKEN));
        IllegalStateException shared = assertThrows(
                IllegalStateException.class,
                () -> new ReportingServiceCredentials(READER_TOKEN, READER_TOKEN));

        assertEquals(
                "Reporting Gateway token must contain at least 32 bytes.",
                missing.getMessage());
        assertEquals(missing.getMessage(), shortToken.getMessage());
        assertEquals(
                "Reporting service identities must be distinct.",
                shared.getMessage());
    }
}
