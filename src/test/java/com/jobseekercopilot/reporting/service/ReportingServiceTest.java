package com.jobseekercopilot.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.jobseekercopilot.generated.userprofileservice.model.Aspirations;
import com.jobseekercopilot.generated.userprofileservice.model.UserProfile;
import com.jobseekercopilot.reporting.dto.ActivityTimelineItem;
import com.jobseekercopilot.reporting.dto.ApplicationSummary;
import com.jobseekercopilot.reporting.dto.CommitmentProgress;
import com.jobseekercopilot.reporting.service.ReportingService.ApplicationRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportingServiceTest {
    private final ReportingService service = new ReportingService(null, null, "http://application-tracker-service:8088");

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
                new ActivityTimelineItem(LocalDateTime.of(2026, 10, 5, 9, 0), "APPLIED",
                        "Software Developer", "Matchtech", "Applied for Software Developer at Matchtech."),
                new ActivityTimelineItem(LocalDateTime.of(2026, 10, 3, 9, 0), "DOCUMENTS_GENERATED",
                        "Software Developer", "Matchtech", "Generated CV and cover letter for Software Developer at Matchtech.")));

        assertThat(journal).isEqualTo("""
                05/10/2026 - Applied for Software Developer at Matchtech.
                03/10/2026 - Generated CV and cover letter for Software Developer at Matchtech.""");
    }

    @Test
    void estimatesCommitmentProgressFromProfileWeeklyHours() {
        UserProfile profile = new UserProfile()
                .aspirations(new Aspirations().targetWeeklyHours(Aspirations.TargetWeeklyHoursEnum.PART_TIME_16_30));

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
