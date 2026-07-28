package com.jobseekercopilot.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jobseekercopilot.reporting.dto.ActivityTimelineItem;
import com.jobseekercopilot.reporting.dto.ApplicationSummary;
import com.jobseekercopilot.reporting.dto.CommitmentProgress;
import com.jobseekercopilot.reporting.dto.ReportingSummaryResponse;
import com.jobseekercopilot.reporting.security.ReportingServiceCredentials;
import com.jobseekercopilot.reporting.service.ReportingService.AspirationsView;
import com.jobseekercopilot.reporting.service.ReportingService.ApplicationRecord;
import com.jobseekercopilot.reporting.service.ReportingService.UserProfileView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class ReportingServiceTest {
    private static final String GATEWAY_TOKEN =
            "test-only-reporting-gateway-token-32-bytes";
    private static final String READER_TOKEN =
            "test-only-application-reader-token-32-bytes";
    private final ReportingService service = new ReportingService(
            null,
            new ReportingServiceCredentials(GATEWAY_TOKEN, READER_TOKEN),
            "http://application-tracker-service:8088",
            "http://user-profile-service:8085");

    @Test
    void countsApplicationStatuses() {
        ApplicationSummary summary = service.applicationSummary(List.of(
                record("DOCUMENTS_GENERATED", "Developer", "A", 1),
                record("APPLIED", "Gardener", "B", 2),
                record("INTERVIEW", "Tester", "C", 3),
                record("UNSUCCESSFUL", "Analyst", "D", 4),
                record("OFFER", "Engineer", "E", 5),
                record("ACCEPTED", "Designer", "F", 6),
                record("REJECTED_BY_USER", "Manager", "G", 7)));

        assertThat(summary.documentsGenerated()).isEqualTo(1);
        assertThat(summary.applied()).isEqualTo(1);
        assertThat(summary.interview()).isEqualTo(1);
        assertThat(summary.unsuccessful()).isEqualTo(1);
        assertThat(summary.offer()).isEqualTo(1);
        assertThat(summary.accepted()).isEqualTo(1);
        assertThat(summary.rejectedByUser()).isEqualTo(1);
        assertThat(summary.total()).isEqualTo(7);
    }

    @Test
    void buildsTimelineNewestFirstAndLimitsToTen() {
        List<ApplicationRecord> records = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> record("APPLIED", "Role " + index, "Company " + index, index))
                .toList();

        List<ActivityTimelineItem> timeline = service.timeline(records);

        assertThat(timeline).hasSize(10);
        assertThat(timeline.get(0).text()).isEqualTo("Applied for Role 12 at Company 12.");
        assertThat(timeline.get(9).text()).isEqualTo("Applied for Role 3 at Company 3.");
    }

    @Test
    void generatesPlainTextJournal() {
        String journal = service.journalText(List.of(
                new ActivityTimelineItem("application-1", LocalDateTime.of(2026, 10, 5, 9, 0), "STATUS_CHANGED", "APPLICATION", "APPLIED",
                        "reed", "Software Developer", "Matchtech", "Applied for Software Developer at Matchtech."),
                new ActivityTimelineItem("application-1", LocalDateTime.of(2026, 10, 3, 9, 0), "DOCUMENT_REFERENCE_CHANGED", "DOCUMENT", "DOCUMENTS_GENERATED",
                        "reed", "Software Developer", "Matchtech", "Generated CV and cover letter for Software Developer at Matchtech.")));

        assertThat(journal).isEqualTo("""
                05/10/2026 - Applied for Software Developer at Matchtech.
                03/10/2026 - Generated CV and cover letter for Software Developer at Matchtech.""");
    }

    @Test
    void estimatesCommitmentProgressFromProfileWeeklyHours() {
        UserProfileView profile = new UserProfileView(
                new AspirationsView("PART_TIME_16_30"));

        CommitmentProgress progress = service.commitmentProgress(List.of(
                record("DOCUMENTS_GENERATED", "Developer", "A", 1),
                record("APPLIED", "Gardener", "B", 2),
                record("INTERVIEW", "Tester", "C", 3),
                record("REJECTED", "Analyst", "D", 4)), profile);

        assertThat(progress.requiredHours()).isEqualByComparingTo(BigDecimal.valueOf(30));
        assertThat(progress.completedHours()).isEqualByComparingTo("4.75");
        assertThat(progress.remainingHours()).isEqualByComparingTo("25.25");
        assertThat(progress.percentageComplete()).isEqualTo(16);
        assertThat(progress.remainingText()).isEqualTo("25.25 hours remaining this week.");
    }

    @Test
    void ownerScopesTrackerAndValidatedBearerScopesProfile() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        server.expect(requestTo(
                        "http://application-tracker-service:8088"
                        + "/api/v1/applications/user/subject-123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Service-Token", READER_TOKEN))
                .andExpect(header("X-Application-Owner", "subject-123"))
                .andRespond(withSuccess(
                        """
                        [{
                          "id": "application-1",
                          "userId": "subject-123",
                          "provider": "test",
                          "jobTitle": "Developer",
                          "companyName": "Example Ltd",
                          "status": "APPLIED",
                          "createdAt": "2026-10-01T09:00:00",
                          "updatedAt": "2026-10-01T10:00:00",
                          "appliedAt": "2026-10-01T11:00:00"
                        }]
                        """,
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://application-tracker-service:8088/api/v1/applications/application-1/history?size=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Service-Token", READER_TOKEN))
                .andExpect(header("X-Application-Owner", "subject-123"))
                .andRespond(withSuccess(
                        """
                        {
                          "applicationId": "application-1",
                          "events": [{
                            "eventType": "APPLICATION_CREATED",
                            "toStatus": "DOCUMENTS_GENERATED",
                            "occurredAt": "2026-10-01T09:00:00Z"
                          }, {
                            "eventType": "DOCUMENT_REFERENCE_CHANGED",
                            "toStatus": "DOCUMENTS_GENERATED",
                            "occurredAt": "2026-10-01T10:00:00Z"
                          }, {
                            "eventType": "STATUS_CHANGED",
                            "fromStatus": "DOCUMENTS_GENERATED",
                            "toStatus": "APPLIED",
                            "occurredAt": "2026-10-01T11:00:00Z"
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://user-profile-service:8085/api/profiles/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess(
                        """
                        {"aspirations":{"targetWeeklyHours":"PART_TIME_16_30"}}
                        """,
                        MediaType.APPLICATION_JSON));
        ReportingService boundary = new ReportingService(
                restTemplate,
                new ReportingServiceCredentials(GATEWAY_TOKEN, READER_TOKEN),
                "http://application-tracker-service:8088",
                "http://user-profile-service:8085");

        ReportingSummaryResponse response = boundary.summary(
                "subject-123",
                "access-token");

        assertThat(response.userId()).isEqualTo("subject-123");
        assertThat(response.applicationSummary().applied()).isEqualTo(1);
        assertThat(response.commitmentProgress().requiredHours())
                .isEqualByComparingTo("30");
        assertThat(response.activityTimeline())
                .extracting(ActivityTimelineItem::evidenceCategory)
                .containsExactly("APPLICATION", "DOCUMENT", "JOB_SEARCH");
        assertThat(response.activityTimeline())
                .extracting(ActivityTimelineItem::text)
                .containsExactly(
                        "Applied for Developer at Example Ltd.",
                        "Generated or linked application documents for Developer at Example Ltd.",
                        "Saved Developer at Example Ltd from test and started tracking it.");
        server.verify();
    }

    private ApplicationRecord record(String status, String jobTitle, String companyName, int day) {
        return new ApplicationRecord(
                null,
                "user-1",
                "job-" + day,
                "test",
                null,
                jobTitle,
                companyName,
                "London",
                "cv-" + day,
                "cl-" + day,
                status,
                LocalDateTime.of(2026, 10, day, 9, 0),
                LocalDateTime.of(2026, 10, day, 10, 0),
                "APPLIED".equals(status) ? LocalDateTime.of(2026, 10, day, 11, 0) : null);
    }
}
