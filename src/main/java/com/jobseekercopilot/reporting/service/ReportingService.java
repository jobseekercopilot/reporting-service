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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
    private final String documentStoreBaseUrl;
    private final String userProfileBaseUrl;

    public ReportingService(
            RestTemplate restTemplate,
            ReportingServiceCredentials credentials,
            @Value("${services.application-tracker-service.base-url:http://application-tracker-service:8088}")
            String applicationTrackerBaseUrl,
            @Value("${services.document-store-service.base-url:http://document-store-service:8089}")
            String documentStoreBaseUrl,
            @Value("${services.user-profile-service.base-url:http://user-profile-service:8085}")
            String userProfileBaseUrl) {
        this.restTemplate = restTemplate;
        this.credentials = credentials;
        this.applicationTrackerBaseUrl = applicationTrackerBaseUrl;
        this.documentStoreBaseUrl = documentStoreBaseUrl;
        this.userProfileBaseUrl = userProfileBaseUrl;
    }

    public ReportingSummaryResponse summary(String owner, String accessToken) {
        long startedAt = System.nanoTime();
        log.info("Reporting summary generation started");
        validateOwner(owner);
        List<ApplicationRecord> applications = applicationsFor(owner);
        UserProfileView profile = profileFor(accessToken);
        List<ActivityTimelineItem> timeline = timeline(owner, applications);
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
        List<ApplicationRecord> applications = applicationsFor(owner);
        List<ActivityTimelineItem> timeline = timeline(owner, applications);
        log.info("UC journal generation completed timelineCount={} durationMs={}",
                timeline.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return new UcJournalResponse(owner, journalText(timeline));
    }

    public String evidenceExport(String owner, String accessToken) {
        ReportingSummaryResponse report = summary(owner, accessToken);
        StringBuilder evidence = new StringBuilder();
        evidence.append("Job Seeker Copilot work-search evidence\n")
                .append("Generated: ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("\n\n")
                .append("Application summary\n")
                .append("Tracked: ").append(report.applicationSummary().total()).append("\n")
                .append("Applied: ").append(report.applicationSummary().applied()).append("\n")
                .append("Interviews: ").append(report.applicationSummary().interview()).append("\n")
                .append("Offers: ").append(report.applicationSummary().offer()).append("\n\n")
                .append("Persisted activity evidence\n");
        String journal = report.ucJournalPreview();
        evidence.append(journal == null || journal.isBlank()
                ? "No recorded activity.\n"
                : journal + "\n");
        evidence.append("\nNote: This export is a user aid assembled from persisted Job Seeker Copilot records. Review it before sharing; it is not an official Universal Credit submission or measured time log.\n");
        return evidence.toString();
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

    List<ActivityTimelineItem> timeline(String owner, List<ApplicationRecord> applications) {
        var applicationActivity = applications.stream()
                .flatMap(application -> {
                    List<ApplicationEvent> events = historyFor(owner, application);
                    boolean hasExplicitDocumentEvent = events.stream()
                            .map(ApplicationEvent::eventType)
                            .anyMatch(this::isDocumentEvent);
                    return events.stream()
                            .flatMap(event -> toTimelineItems(application, event, hasExplicitDocumentEvent).stream());
                }).toList();
        return java.util.stream.Stream.concat(
                        applicationActivity.stream(),
                        documentActivityFor(owner).stream())
                .filter(Objects::nonNull)
                .sorted(activityOrder())
                .limit(25)
                .toList();
    }

    private Comparator<ActivityTimelineItem> activityOrder() {
        return Comparator.comparing(ActivityTimelineItem::occurredAt)
                .reversed()
                .thenComparing(ActivityTimelineItem::eventType)
                .thenComparing(item -> blankToFallback(item.applicationId(), ""))
                .thenComparing(ActivityTimelineItem::text);
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
        List<ApplicationRecord> safeApplications = applications == null
                ? List.of()
                : applications.stream()
                        .filter(application -> owner.equals(application.userId()))
                        .toList();
        log.info("Application Tracker reporting lookup completed count={} durationMs={}",
                safeApplications.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return safeApplications;
    }

    private List<ApplicationEvent> historyFor(
            String owner, ApplicationRecord application) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Token", credentials.applicationTrackerReaderToken());
        headers.set("X-Application-Owner", owner);
        List<ApplicationEvent> events = new ArrayList<>();
        Set<UUID> seenEventIds = new HashSet<>();
        int page = 0;
        int totalPages;
        do {
            ApplicationHistory history = restTemplate.exchange(
                    applicationTrackerBaseUrl
                            + "/api/v1/applications/{applicationId}/history?page={page}&size=100",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    ApplicationHistory.class,
                    application.id(),
                    page).getBody();
            if (history == null) {
                break;
            }
            if (history.events() != null) {
                history.events().stream()
                        .filter(event -> event.id() != null)
                        .filter(event -> application.id() != null
                                && event.applicationId() != null
                                && application.id().equals(
                                        event.applicationId().toString()))
                        .filter(event -> seenEventIds.add(event.id()))
                        .forEach(events::add);
            }
            totalPages = Math.max(history.totalPages(), 1);
            page++;
        } while (page < totalPages && page < 1000);
        return events;
    }

    private List<ActivityTimelineItem> documentActivityFor(String owner) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Token", credentials.documentStoreReaderToken());
        headers.set("X-Document-Owner", owner);
        List<ActivityTimelineItem> items = new ArrayList<>();
        Set<UUID> seenEventIds = new HashSet<>();
        int page = 0;
        int totalPages;
        try {
            do {
                DocumentActivityPage activity = restTemplate.exchange(
                        documentStoreBaseUrl
                                + "/api/v1/document-activity?page={page}&size=100",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        DocumentActivityPage.class,
                        page).getBody();
                if (activity == null) {
                    break;
                }
                if (activity.items() != null) {
                    activity.items().stream()
                            .filter(event -> event.id() != null
                                    && seenEventIds.add(event.id()))
                            .map(this::toTimelineItem)
                            .filter(Objects::nonNull)
                            .forEach(items::add);
                }
                totalPages = Math.max(activity.totalPages(), 1);
                page++;
            } while (page < totalPages && page < 1000);
        } catch (RestClientException exception) {
            log.warn(
                    "Document Store reporting lookup failed error={}",
                    exception.getClass().getSimpleName());
        }
        return items;
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
                application.id(),
                occurredAt,
                "CURRENT_STATUS",
                categoryForStatus(status),
                status,
                application.provider(),
                application.jobTitle(),
                application.companyName(),
                withSourceEvidence(
                        message(status, application.jobTitle(), application.companyName()), application));
    }

    List<ActivityTimelineItem> toTimelineItems(
            ApplicationRecord application,
            ApplicationEvent event,
            boolean hasExplicitDocumentEvent) {
        if ("APPLICATION_DOCUMENT_SELECTED".equals(event.eventType())) {
            List<ActivityTimelineItem> items = new ArrayList<>();
            items.add(applicationActivity(
                    application,
                    event,
                    "APPLICATION_DOCUMENT_PLAN_SELECTED",
                    "DOCUMENT",
                    "Selected application document choices for %s at %s."));
            if (selectionIntroducesDocument(event.reason())) {
                items.add(applicationActivity(
                        application,
                        event,
                        "DOCUMENT_LINKED_TO_APPLICATION",
                        "DOCUMENT",
                        "Linked selected document versions to the application for %s at %s."));
            }
            return items.stream().filter(Objects::nonNull).toList();
        }
        if ("APPLICATION_DOCUMENT_SELECTION_CHANGED".equals(event.eventType())) {
            List<ActivityTimelineItem> items = new ArrayList<>();
            if (selectionIntroducesDocument(event.reason())) {
                items.add(applicationActivity(
                        application,
                        event,
                        "DOCUMENT_LINKED_TO_APPLICATION",
                        "DOCUMENT",
                        "Linked selected document versions to the application for %s at %s."));
            }
            if (selectionReplacesDocument(event.reason())) {
                items.add(applicationActivity(
                        application,
                        event,
                        "DOCUMENT_REPLACED",
                        "DOCUMENT",
                        "Replaced an application document selection for %s at %s."));
            }
            if (items.isEmpty()) {
                items.add(applicationActivity(
                        application,
                        event,
                        event.eventType(),
                        "DOCUMENT",
                        "Changed application document choices for %s at %s."));
            }
            return items.stream().filter(Objects::nonNull).toList();
        }
        ActivityTimelineItem primary = toTimelineItem(application, event);
        if (primary == null) {
            return List.of();
        }
        if (!hasExplicitDocumentEvent
                && "APPLICATION_CREATED".equals(event.eventType())
                && "DOCUMENTS_GENERATED".equals(primary.status())) {
            return List.of(
                    primary,
                    new ActivityTimelineItem(
                            application.id(),
                            primary.occurredAt(),
                            "DOCUMENTS_GENERATED",
                            "DOCUMENT",
                            primary.status(),
                            application.provider(),
                            application.jobTitle(),
                            application.companyName(),
                            withSourceEvidence(
                                    message("DOCUMENTS_GENERATED", application.jobTitle(), application.companyName()), application)));
        }
        return List.of(primary);
    }

    private ActivityTimelineItem toTimelineItem(
            ApplicationRecord application,
            ApplicationEvent event) {
        if (event.occurredAt() == null || event.eventType() == null) {
            return null;
        }
        String status = blankToFallback(event.toStatus(), statusName(application));
        String text = eventMessage(event.eventType(), status, application);
        if (text == null) {
            return null;
        }
        return new ActivityTimelineItem(
                application.id(),
                LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC),
                event.eventType(),
                categoryForEvent(event.eventType()),
                status,
                application.provider(),
                application.jobTitle(),
                application.companyName(),
                text);
    }

    private ActivityTimelineItem applicationActivity(
            ApplicationRecord application,
            ApplicationEvent event,
            String reportedEventType,
            String category,
            String messageTemplate) {
        if (event.occurredAt() == null) {
            return null;
        }
        String job = blankToFallback(application.jobTitle(), "role");
        String company = blankToFallback(
                application.companyName(), "the employer");
        return new ActivityTimelineItem(
                application.id(),
                LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC),
                reportedEventType,
                category,
                blankToFallback(event.toStatus(), statusName(application)),
                application.provider(),
                application.jobTitle(),
                application.companyName(),
                messageTemplate.formatted(job, company));
    }

    private ActivityTimelineItem toTimelineItem(DocumentActivityEvent event) {
        if (event.occurredAt() == null
                || event.eventType() == null
                || event.documentType() == null
                || event.version() < 1
                || !("CV".equals(event.documentType())
                        || "COVER_LETTER".equals(event.documentType()))) {
            return null;
        }
        String document = documentLabel(event.documentType());
        String text = switch (event.eventType()) {
            case "DOCUMENT_VERSION_CREATED" ->
                    "Created %s version %d.".formatted(document, event.version());
            case "DOCUMENT_UPLOADED" ->
                    "Uploaded %s version %d."
                            .formatted(document, event.version());
            case "DOCUMENT_LINKED_TO_APPLICATION" ->
                    "Linked %s version %d to an application."
                            .formatted(document, event.version());
            case "DOCUMENT_REPLACED" ->
                    "Replaced %s with version %d."
                            .formatted(document, event.version());
            case "DOCUMENT_DELETED" ->
                    "Removed %s version %d."
                            .formatted(document, event.version());
            case "DOCUMENT_VERSION_DOWNLOADED" ->
                    "Downloaded %s %s version %d."
                            .formatted(
                                    "PREVIOUS_VERSION".equals(event.result())
                                            ? "previous"
                                            : "current",
                                    document,
                                    event.version());
            case "DOCUMENT_CURRENT_VERSION_CHANGED" ->
                    "Made %s version %d current."
                            .formatted(document, event.version());
            case "DOCUMENT_VERSION_ARCHIVED" ->
                    "Archived %s version %d."
                            .formatted(document, event.version());
            case "DOCUMENT_VERSION_RESTORED" ->
                    "Restored %s version %d."
                            .formatted(document, event.version());
            default -> null;
        };
        if (text == null) {
            return null;
        }
        if ("DOCUMENT_UPLOADED".equals(event.eventType())
                && !"UPLOADED".equals(event.source())) {
            return null;
        }
        return new ActivityTimelineItem(
                null,
                LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC),
                event.eventType(),
                "DOCUMENT",
                blankToFallback(event.result(), "RECORDED"),
                "DOCUMENT_STORE",
                null,
                null,
                text);
    }

    private String eventMessage(String eventType, String status, ApplicationRecord application) {
        String job = blankToFallback(application.jobTitle(), "role");
        String company = blankToFallback(application.companyName(), "the employer");
        String message = switch (eventType) {
            case "APPLICATION_CREATED" -> "Saved %s at %s from %s and started tracking it."
                    .formatted(job, company, blankToFallback(application.provider(), "job search"));
            case "APPLICATION_SAVED" ->
                    "Saved %s at %s to My Applications."
                            .formatted(job, company);
            case "STATUS_CHANGED" -> message(status, application.jobTitle(), application.companyName());
            case "DOCUMENT_REFERENCE_CHANGED" -> "Generated or linked application documents for %s at %s.".formatted(job, company);
            case "DOCUMENT_REFERENCES_RECONCILED" -> "Verified stored application documents for %s at %s.".formatted(job, company);
            case "APPLICATION_DOCUMENT_PLAN_SELECTED" ->
                    "Selected application document choices for %s at %s."
                            .formatted(job, company);
            case "DOCUMENT_LINKED_TO_APPLICATION" ->
                    "Linked selected document versions to the application for %s at %s."
                            .formatted(job, company);
            case "DOCUMENT_REPLACED" ->
                    "Replaced an application document selection for %s at %s."
                            .formatted(job, company);
            case "DOCUMENT_DELETED" ->
                    "Removed an application document selection for %s at %s."
                            .formatted(job, company);
            case "APPLICATION_DOCUMENTS_FROZEN" -> "Froze the exact application document choices when applying for %s at %s.".formatted(job, company);
            case "GENERATED_APPLICATION_WITHDRAWN" -> "Withdrew generated application materials for %s at %s.".formatted(job, company);
            case "APPLICATION_DELETED" -> "Removed the tracked application for %s at %s.".formatted(job, company);
            case "LEGACY_SNAPSHOT" -> "Recorded the existing application state for %s at %s.".formatted(job, company);
            default -> null;
        };
        return withSourceEvidence(message, application);
    }

    private String withSourceEvidence(
            String message, ApplicationRecord application) {
        if (!"NHS_JOBS".equals(application.provider())) {
            return message;
        }
        return message + " Source evidence: provider=NHS_JOBS"
                + "; externalVacancyReference="
                + blankToFallback(application.externalJobId(), "unknown")
                + "; canonicalJobId="
                + blankToFallback(application.canonicalJobId(), "unknown")
                + "; listingUrl="
                + blankToFallback(application.listingUrl(), "missing")
                + "; applicationUrl="
                + blankToFallback(application.applyUrl(), "not-separate")
                + "; attribution="
                + blankToFallback(application.attributionLabel(), "missing")
                + "; attributionSourceUrl="
                + blankToFallback(application.attributionSourceUrl(), "missing")
                + "; licenceUrl="
                + blankToFallback(application.licenceUrl(), "missing")
                + "; noEndorsement="
                + blankToFallback(application.disclaimer(), "missing")
                + ".";
    }

    private boolean isDocumentEvent(String eventType) {
        return "DOCUMENT_REFERENCE_CHANGED".equals(eventType)
                || "DOCUMENT_REFERENCES_RECONCILED".equals(eventType)
                || "APPLICATION_DOCUMENT_SELECTED".equals(eventType)
                || "APPLICATION_DOCUMENT_SELECTION_CHANGED".equals(eventType)
                || "APPLICATION_DOCUMENT_PLAN_SELECTED".equals(eventType)
                || "DOCUMENT_LINKED_TO_APPLICATION".equals(eventType)
                || "DOCUMENT_REPLACED".equals(eventType)
                || "DOCUMENT_DELETED".equals(eventType)
                || "APPLICATION_DOCUMENTS_FROZEN".equals(eventType)
                || "GENERATED_APPLICATION_WITHDRAWN".equals(eventType);
    }

    private boolean selectionIntroducesDocument(String reason) {
        return normalizedSelectionReason(reason).contains(" selected");
    }

    private boolean selectionReplacesDocument(String reason) {
        return normalizedSelectionReason(reason).contains(" changed");
    }

    private String normalizedSelectionReason(String reason) {
        return reason == null ? "" : reason.toLowerCase(Locale.ROOT);
    }

    private String categoryForEvent(String eventType) {
        return switch (eventType) {
            case "APPLICATION_CREATED" -> "JOB_SEARCH";
            case "DOCUMENT_REFERENCE_CHANGED",
                    "DOCUMENT_REFERENCES_RECONCILED",
                    "APPLICATION_DOCUMENT_PLAN_SELECTED",
                    "DOCUMENT_LINKED_TO_APPLICATION",
                    "DOCUMENT_REPLACED",
                    "DOCUMENT_DELETED",
                    "APPLICATION_DOCUMENTS_FROZEN",
                    "GENERATED_APPLICATION_WITHDRAWN" -> "DOCUMENT";
            default -> "APPLICATION";
        };
    }

    private String categoryForStatus(String status) {
        return "DOCUMENTS_GENERATED".equals(status) ? "DOCUMENT" : "APPLICATION";
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

    private String documentLabel(String documentType) {
        return switch (documentType) {
            case "CV" -> "CV";
            case "COVER_LETTER" -> "cover letter";
            default -> "document";
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
            String canonicalJobId,
            String provider,
            String externalJobId,
            String listingUrl,
            String applyUrl,
            String attributionLabel,
            String attributionSourceUrl,
            String licenceUrl,
            String disclaimer,
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

    record ApplicationHistory(
            String applicationId,
            List<ApplicationEvent> events,
            int page,
            int totalPages) {
    }

    record ApplicationEvent(
            UUID id,
            UUID applicationId,
            String eventType,
            String fromStatus,
            String toStatus,
            Instant occurredAt,
            String reason,
            long recordVersion) {
        ApplicationEvent(
                String eventType,
                String fromStatus,
                String toStatus,
                Instant occurredAt) {
            this(
                    null,
                    null,
                    eventType,
                    fromStatus,
                    toStatus,
                    occurredAt,
                    null,
                    0);
        }
    }

    record DocumentActivityPage(
            List<DocumentActivityEvent> items,
            int page,
            int totalPages) {
    }

    record DocumentActivityEvent(
            UUID id,
            String eventType,
            String documentType,
            String source,
            int version,
            String result,
            Instant occurredAt) {
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
