package com.jobseekercopilot.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CommitmentProgress(
        BigDecimal requiredHours,
        BigDecimal completedHours,
        BigDecimal remainingHours,
        int percentageComplete,
        LocalDate periodStart,
        LocalDate periodEnd,
        String remainingText) {
}
