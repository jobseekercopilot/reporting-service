package com.jobseekercopilot.reporting.controller;

import com.jobseekercopilot.reporting.dto.ReportingSummaryResponse;
import com.jobseekercopilot.reporting.dto.UcJournalResponse;
import com.jobseekercopilot.reporting.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {
    private static final String USER_ID_HEADER = "X-User-Id";
    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get reporting summary")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporting summary returned"),
            @ApiResponse(responseCode = "400", description = "Missing X-User-Id header"),
            @ApiResponse(responseCode = "502", description = "Downstream service failed")
    })
    public ResponseEntity<ReportingSummaryResponse> summary(
            @Parameter(in = ParameterIn.HEADER, name = USER_ID_HEADER, required = true)
            @RequestHeader(USER_ID_HEADER) String userId) {
        return ResponseEntity.ok(reportingService.summary(userId));
    }

    @GetMapping("/uc-journal")
    @Operation(summary = "Get deterministic Universal Credit journal text")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "UC journal text returned"),
            @ApiResponse(responseCode = "400", description = "Missing X-User-Id header"),
            @ApiResponse(responseCode = "502", description = "Downstream service failed")
    })
    public ResponseEntity<UcJournalResponse> ucJournal(
            @Parameter(in = ParameterIn.HEADER, name = USER_ID_HEADER, required = true)
            @RequestHeader(USER_ID_HEADER) String userId) {
        return ResponseEntity.ok(reportingService.ucJournal(userId));
    }
}
