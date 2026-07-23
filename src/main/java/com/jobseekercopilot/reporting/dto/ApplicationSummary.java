package com.jobseekercopilot.reporting.dto;

public record ApplicationSummary(
        int documentsGenerated,
        int applied,
        int interview,
        int unsuccessful,
        int offer,
        int accepted,
        int rejectedByUser,
        int total) {
}
