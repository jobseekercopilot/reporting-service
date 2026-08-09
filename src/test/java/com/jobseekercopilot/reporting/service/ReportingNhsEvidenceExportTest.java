package com.jobseekercopilot.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jobseekercopilot.reporting.security.ReportingServiceCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class ReportingNhsEvidenceExportTest {

    private static final String READER_TOKEN =
            "test-only-application-reader-token-32-bytes";

    @Test
    void authoritativeNhsSourceSurvivesTrackerReloadIntoEvidenceExport() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        ReportingService reporting = new ReportingService(
                restTemplate,
                new ReportingServiceCredentials(
                        "test-only-reporting-gateway-token-32-bytes",
                        READER_TOKEN),
                "http://application-tracker-service:8088",
                "http://user-profile-service:8085");

        server.expect(requestTo(
                        "http://application-tracker-service:8088"
                                + "/api/v1/applications/user/nhs-owner"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Service-Token", READER_TOKEN))
                .andExpect(header("X-Application-Owner", "nhs-owner"))
                .andRespond(withSuccess(
                        """
                        [{
                          "id": "application-nhs-c123",
                          "userId": "nhs-owner",
                          "jobId": "canonical-nhs-c123",
                          "canonicalJobId": "canonical-nhs-c123",
                          "provider": "NHS_JOBS",
                          "externalJobId": "C123",
                          "listingUrl": "https://www.jobs.nhs.uk/candidate/jobadvert/C123",
                          "applyUrl": "https://www.jobs.nhs.uk/candidate/jobadvert/C123",
                          "attributionLabel": "Vacancy source: NHS Jobs",
                          "attributionSourceUrl": "https://www.jobs.nhs.uk/",
                          "licenceUrl": "https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/",
                          "disclaimer": "NHS Jobs does not endorse Job Seeker Copilot.",
                          "jobTitle": "Community Staff Nurse",
                          "companyName": "Example NHS Trust",
                          "location": "London",
                          "status": "APPLIED",
                          "createdAt": "2026-10-01T09:00:00",
                          "updatedAt": "2026-10-01T09:00:00",
                          "appliedAt": "2026-10-01T09:00:00"
                        }]
                        """,
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://application-tracker-service:8088"
                                + "/api/v1/applications/application-nhs-c123/history?size=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Service-Token", READER_TOKEN))
                .andExpect(header("X-Application-Owner", "nhs-owner"))
                .andRespond(withSuccess(
                        """
                        {
                          "applicationId": "application-nhs-c123",
                          "events": [{
                            "eventType": "APPLICATION_CREATED",
                            "toStatus": "APPLIED",
                            "occurredAt": "2026-10-01T09:00:00Z"
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://user-profile-service:8085/api/profiles/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess(
                        "{\"aspirations\":{\"targetWeeklyHours\":\"FULL_TIME\"}}",
                        MediaType.APPLICATION_JSON));

        String evidence = reporting.evidenceExport(
                "nhs-owner", "access-token");

        assertThat(evidence)
                .contains("provider=NHS_JOBS")
                .contains("externalVacancyReference=C123")
                .contains("canonicalJobId=canonical-nhs-c123")
                .contains("listingUrl=https://www.jobs.nhs.uk/candidate/jobadvert/C123")
                .contains("applicationUrl=https://www.jobs.nhs.uk/candidate/jobadvert/C123")
                .contains("attribution=Vacancy source: NHS Jobs")
                .contains("licenceUrl=https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/")
                .contains("noEndorsement=NHS Jobs does not endorse Job Seeker Copilot.");
        server.verify();
    }
}
