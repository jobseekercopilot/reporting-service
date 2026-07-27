package com.jobseekercopilot.reporting.controller;

import com.jobseekercopilot.reporting.dto.ReportingSummaryResponse;
import com.jobseekercopilot.reporting.dto.UcJournalResponse;
import com.jobseekercopilot.reporting.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {
    private static final String OWNER_HEADER = "X-Report-Owner";
    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get reporting summary")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporting summary returned"),
            @ApiResponse(responseCode = "401", description = "Authorized Gateway required"),
            @ApiResponse(responseCode = "502", description = "Downstream service failed")
    })
    public ResponseEntity<ReportingSummaryResponse> summary(
            @Parameter(in = ParameterIn.HEADER, name = OWNER_HEADER, required = true)
            @RequestHeader(OWNER_HEADER) String owner,
            @RequestHeader("Authorization") String authorization) {
        return privateResponse(reportingService.summary(
                owner,
                bearerToken(authorization)));
    }

    @GetMapping("/uc-journal")
    @Operation(summary = "Get deterministic Universal Credit journal text")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "UC journal text returned"),
            @ApiResponse(responseCode = "401", description = "Authorized Gateway required"),
            @ApiResponse(responseCode = "502", description = "Downstream service failed")
    })
    public ResponseEntity<UcJournalResponse> ucJournal(
            @Parameter(in = ParameterIn.HEADER, name = OWNER_HEADER, required = true)
            @RequestHeader(OWNER_HEADER) String owner,
            @RequestHeader("Authorization") String authorization) {
        return privateResponse(reportingService.ucJournal(
                owner,
                bearerToken(authorization)));
    }

    private String bearerToken(String authorization) {
        return authorization.substring("Bearer ".length()).trim();
    }

    private <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}
