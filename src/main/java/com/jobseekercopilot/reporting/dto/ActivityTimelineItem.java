package com.jobseekercopilot.reporting.dto;

import java.time.LocalDateTime;

public record ActivityTimelineItem(
        String applicationId,
        LocalDateTime occurredAt,
        String eventType,
        String evidenceCategory,
        String status,
        String provider,
        String jobTitle,
        String companyName,
        String text) {
}
