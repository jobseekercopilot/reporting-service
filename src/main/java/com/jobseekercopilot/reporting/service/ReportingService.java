package com.jobseekercopilot.reporting.service;

import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import com.jobseekercopilot.generated.userprofileservice.model.Aspirations;
import com.jobseekercopilot.generated.userprofileservice.model.UserProfile;
import com.jobseekercopilot.reporting.dto.ActivityTimelineItem;
import com.jobseekercopilot.reporting.dto.ApplicationSummary;
import com.jobseekercopilot.reporting.dto.CommitmentProgress;
import com.jobseekercopilot.reporting.dto.ReportingSummaryResponse;
import com.jobseekercopilot.reporting.dto.UcJournalResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class ReportingService {
    private static final Logger log = LoggerFactory.getLogger(ReportingService.class);
    private static final DateTimeFormatter JOURNAL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal DEFAULT_REQUIRED_HOURS = BigDecimal.valueOf(35);

    private final RestTemplate restTemplate;
    private final UserProfilesApi userProfilesApi;
    private final String applicationTrackerBaseUrl;

    public ReportingService(RestTemplate restTemplate,
                            UserProfilesApi userProfilesApi,
                            @Value("${services.application-tracker-service.base-url:http://application-tracker-service:8088}") String applicationTrackerBaseUrl) {
        this.restTemplate = restTemplate;
        this.userProfilesApi = userProfilesApi;
        this.applicationTrackerBaseUrl = applicationTrackerBaseUrl;
    }

    public ReportingSummaryResponse summary(String userId) {
        long startedAt = System.nanoTime();
        log.info("Reporting summary generation started userId={}", userId);
        validateUserId(userId);
        List<ApplicationRecord> applications = applicationsFor(userId);
        UserProfile profile = profileFor(userId);
        List<ActivityTimelineItem> timeline = timeline(applications);
        log.info("Reporting summary generation completed userId={} applicationCount={} timelineCount={} durationMs={}",
                userId,
                applications.size(),
                timeline.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return new ReportingSummaryResponse(
                userId,
                applicationSummary(applications),
                timeline,
                commitmentProgress(applications, profile),
                journalText(timeline));
    }

    public UcJournalResponse ucJournal(String userId) {
        long startedAt = System.nanoTime();
        validateUserId(userId);
        List<ActivityTimelineItem> timeline = timeline(applicationsFor(userId));
        log.info("UC journal generation completed userId={} timelineCount={} durationMs={}",
                userId,
                timeline.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return new UcJournalResponse(userId, journalText(timeline));
    }

    ApplicationSummary applicationSummary(List<ApplicationRecord> applications) {
        return new ApplicationSummary(
                count(applications, "DOCUMENTS_GENERATED"),
                count(applications, "APPLIED"),
                count(applications, "INTERVIEW"),
                count(applications, "UNSUCCESSFUL") + count(applications, "REJECTED"),
                count(applications, "OFFER"),
                count(applications, "ACCEPTED"),
                count(applications, "REJECTED_BY_USER"),
                applications.size());
    }

    List<ActivityTimelineItem> timeline(List<ApplicationRecord> applications) {
        return applications.stream()
                .map(this::toTimelineItem)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ActivityTimelineItem::occurredAt).reversed())
                .limit(10)
                .toList();
    }

    String journalText(List<ActivityTimelineItem> timeline) {
        return timeline.stream()
                .map(item -> "%s - %s".formatted(item.occurredAt().toLocalDate().format(JOURNAL_DATE_FORMAT), item.text()))
                .toList()
                .stream()
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
    }

    CommitmentProgress commitmentProgress(List<ApplicationRecord> applications, UserProfile profile) {
        // V1 estimate: tracker statuses are converted to indicative work-search hours until detailed activity logging exists.
        BigDecimal requiredHours = requiredHours(profile);
        BigDecimal completedHours = applications.stream()
                .map(application -> estimatedHours(statusName(application)))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingHours = requiredHours.subtract(completedHours).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        int percentage = requiredHours.compareTo(BigDecimal.ZERO) == 0
                ? 100
                : completedHours.multiply(BigDecimal.valueOf(100))
                        .divide(requiredHours, 0, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100))
                        .intValue();
        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.with(DayOfWeek.MONDAY);
        LocalDate periodEnd = today.with(DayOfWeek.SUNDAY);
        String remainingText = remainingHours.compareTo(BigDecimal.ZERO) == 0
                ? "Commitment met for this week."
                : "%s hours remaining this week.".formatted(formatHours(remainingHours));
        return new CommitmentProgress(requiredHours, completedHours, remainingHours, percentage, periodStart, periodEnd, remainingText);
    }

    private List<ApplicationRecord> applicationsFor(String userId) {
        long startedAt = System.nanoTime();
        List<ApplicationRecord> applications = restTemplate.exchange(
                applicationTrackerBaseUrl + "/api/v1/applications/user/{userId}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ApplicationRecord>>() {},
                userId).getBody();
        List<ApplicationRecord> safeApplications = applications == null ? List.of() : applications;
        log.info("Application tracker reporting lookup completed userId={} count={} durationMs={}",
                userId,
                safeApplications.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return safeApplications;
    }

    private UserProfile profileFor(String userId) {
        long startedAt = System.nanoTime();
        try {
            UserProfile profile = userProfilesApi.getMyProfile(userId);
            log.info("User profile reporting lookup completed userId={} found={} durationMs={}",
                    userId,
                    profile != null,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return profile;
        } catch (HttpClientErrorException.NotFound exception) {
            log.warn("User profile reporting lookup not found userId={} durationMs={}",
                    userId,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return null;
        } catch (RestClientException exception) {
            log.warn("User profile reporting lookup failed userId={} durationMs={} error={}",
                    userId,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("X-User-Id header is required");
        }
    }

    private int count(List<ApplicationRecord> applications, String status) {
        return (int) applications.stream()
                .filter(application -> status.equals(statusName(application)))
                .count();
    }

    private ActivityTimelineItem toTimelineItem(ApplicationRecord application) {
        LocalDateTime occurredAt = firstPresent(application.appliedAt(), application.updatedAt(), application.createdAt());
        if (occurredAt == null) {
            return null;
        }
        String status = statusName(application);
        return new ActivityTimelineItem(
                occurredAt,
                status,
                application.jobTitle(),
                application.companyName(),
                message(status, application.jobTitle(), application.companyName()));
    }

    private String message(String status, String jobTitle, String companyName) {
        String job = blankToFallback(jobTitle, "role");
        String company = blankToFallback(companyName, "the employer");
        return switch (status) {
            case "DOCUMENTS_GENERATED" -> "Generated CV and cover letter for %s at %s.".formatted(job, company);
            case "APPLIED" -> "Applied for %s at %s.".formatted(job, company);
            case "INTERVIEW" -> "Interview arranged for %s at %s.".formatted(job, company);
            case "UNSUCCESSFUL", "REJECTED" -> "Application unsuccessful for %s at %s.".formatted(job, company);
            case "OFFER" -> "Offer received for %s at %s.".formatted(job, company);
            case "ACCEPTED" -> "Accepted offer for %s at %s.".formatted(job, company);
            case "REJECTED_BY_USER" -> "Declined offer for %s at %s.".formatted(job, company);
            default -> "Updated application for %s at %s.".formatted(job, company);
        };
    }

    private LocalDateTime firstPresent(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String statusName(ApplicationRecord application) {
        return application.status() == null ? "" : application.status();
    }

    record ApplicationRecord(
            String id,
            String userId,
            String jobId,
            String provider,
            String externalJobId,
            String jobTitle,
            String companyName,
            String location,
            String cvDocumentId,
            String coverLetterDocumentId,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime appliedAt) {
    }

    private BigDecimal requiredHours(UserProfile profile) {
        if (profile == null || profile.getAspirations() == null || profile.getAspirations().getTargetWeeklyHours() == null) {
            return DEFAULT_REQUIRED_HOURS;
        }
        Aspirations.TargetWeeklyHoursEnum target = profile.getAspirations().getTargetWeeklyHours();
        return switch (target) {
            case FULL_TIME, FLEXIBLE -> DEFAULT_REQUIRED_HOURS;
            case PART_TIME_16_30 -> BigDecimal.valueOf(30);
            case PART_TIME_UNDER_16 -> BigDecimal.valueOf(16);
        };
    }

    private BigDecimal estimatedHours(String status) {
        return switch (status) {
            case "DOCUMENTS_GENERATED" -> BigDecimal.valueOf(1);
            case "APPLIED" -> BigDecimal.valueOf(1.5);
            case "INTERVIEW" -> BigDecimal.valueOf(2);
            case "UNSUCCESSFUL", "REJECTED" -> BigDecimal.valueOf(0.25);
            default -> BigDecimal.ZERO;
        };
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String formatHours(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
