package com.jobseekercopilot.reporting.dto;

import java.time.LocalDateTime;

public record ActivityTimelineItem(
        LocalDateTime occurredAt,
        String status,
        String jobTitle,
        String companyName,
        String text) {
}
