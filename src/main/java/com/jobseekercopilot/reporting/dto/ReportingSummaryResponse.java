package com.jobseekercopilot.reporting.dto;

import java.util.List;

public record ReportingSummaryResponse(
        String userId,
        ApplicationSummary applicationSummary,
        List<ActivityTimelineItem> activityTimeline,
        CommitmentProgress commitmentProgress,
        String ucJournalPreview) {
}
