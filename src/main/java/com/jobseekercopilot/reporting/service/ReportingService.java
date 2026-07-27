package com.jobseekercopilot.reporting.service;

import com.jobseekercopilot.reporting.dto.ActivityTimelineItem;
import com.jobseekercopilot.reporting.dto.ApplicationSummary;
import com.jobseekercopilot.reporting.dto.CommitmentProgress;
import com.jobseekercopilot.reporting.dto.ReportingSummaryResponse;
import com.jobseekercopilot.reporting.dto.UcJournalResponse;
import com.jobseekercopilot.reporting.security.ReportingServiceCredentials;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
    private final ReportingServiceCredentials credentials;
    private final String applicationTrackerBaseUrl;
    private final String userProfileBaseUrl;

    public ReportingService(
            RestTemplate restTemplate,
            ReportingServiceCredentials credentials,
            @Value("${services.application-tracker-service.base-url:http://application-tracker-service:8088}")
            String applicationTrackerBaseUrl,
            @Value("${services.user-profile-service.base-url:http://user-profile-service:8085}")
            String userProfileBaseUrl) {
        this.restTemplate = restTemplate;
        this.credentials = credentials;
        this.applicationTrackerBaseUrl = applicationTrackerBaseUrl;
        this.userProfileBaseUrl = userProfileBaseUrl;
    }

    public ReportingSummaryResponse summary(String owner, String accessToken) {
        long startedAt = System.nanoTime();
        log.info("Reporting summary generation started");
        validateOwner(owner);
        List<ApplicationRecord> applications = applicationsFor(owner);
        UserProfileView profile = profileFor(accessToken);
        List<ActivityTimelineItem> timeline = timeline(applications);
        log.info("Reporting summary generation completed applicationCount={} timelineCount={} durationMs={}",
                applications.size(),
                timeline.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return new ReportingSummaryResponse(
                owner,
                applicationSummary(applications),
                timeline,
                commitmentProgress(applications, profile),
                journalText(timeline));
    }

    public UcJournalResponse ucJournal(String owner, String accessToken) {
        long startedAt = System.nanoTime();
        validateOwner(owner);
        List<ActivityTimelineItem> timeline = timeline(applicationsFor(owner));
        log.info("UC journal generation completed timelineCount={} durationMs={}",
                timeline.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return new UcJournalResponse(owner, journalText(timeline));
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

    CommitmentProgress commitmentProgress(
            List<ApplicationRecord> applications,
            UserProfileView profile) {
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

    private List<ApplicationRecord> applicationsFor(String owner) {
        long startedAt = System.nanoTime();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Token", credentials.applicationTrackerReaderToken());
        headers.set("X-Application-Owner", owner);
        List<ApplicationRecord> applications = restTemplate.exchange(
                applicationTrackerBaseUrl + "/api/v1/applications/user/{userId}",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<ApplicationRecord>>() {},
                owner).getBody();
        List<ApplicationRecord> safeApplications = applications == null ? List.of() : applications;
        log.info("Application Tracker reporting lookup completed count={} durationMs={}",
                safeApplications.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return safeApplications;
    }

    private UserProfileView profileFor(String accessToken) {
        long startedAt = System.nanoTime();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        try {
            UserProfileView profile = restTemplate.exchange(
                    userProfileBaseUrl + "/api/profiles/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserProfileView.class).getBody();
            log.info("User profile reporting lookup completed found={} durationMs={}",
                    profile != null,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return profile;
        } catch (HttpClientErrorException.NotFound exception) {
            log.warn("User profile reporting lookup not found durationMs={}",
                    (System.nanoTime() - startedAt) / 1_000_000);
            return null;
        } catch (RestClientException exception) {
            log.warn("User profile reporting lookup failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private void validateOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Validated report owner is required");
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

    record UserProfileView(AspirationsView aspirations) {
    }

    record AspirationsView(String targetWeeklyHours) {
    }

    private BigDecimal requiredHours(UserProfileView profile) {
        if (profile == null
                || profile.aspirations() == null
                || profile.aspirations().targetWeeklyHours() == null) {
            return DEFAULT_REQUIRED_HOURS;
        }
        return switch (profile.aspirations().targetWeeklyHours()) {
            case "FULL_TIME", "FLEXIBLE" -> DEFAULT_REQUIRED_HOURS;
            case "PART_TIME_16_30" -> BigDecimal.valueOf(30);
            case "PART_TIME_UNDER_16" -> BigDecimal.valueOf(16);
            default -> DEFAULT_REQUIRED_HOURS;
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
