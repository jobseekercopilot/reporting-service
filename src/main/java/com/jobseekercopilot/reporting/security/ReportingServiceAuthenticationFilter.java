package com.jobseekercopilot.reporting.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ReportingServiceAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log =
            LoggerFactory.getLogger(ReportingServiceAuthenticationFilter.class);
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String OWNER_HEADER = "X-Report-Owner";

    private final ReportingServiceCredentials credentials;

    public ReportingServiceAuthenticationFilter(
            ReportingServiceCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/reports/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        List<String> serviceTokens = values(request, SERVICE_TOKEN_HEADER);
        List<String> owners = values(request, OWNER_HEADER);
        List<String> authorization = values(request, "Authorization");

        if (serviceTokens.size() != 1
                || !matches(serviceTokens.get(0), credentials.gatewayToken())
                || owners.size() != 1
                || owners.get(0).isBlank()
                || authorization.size() != 1
                || !validBearer(authorization.get(0))) {
            log.warn("Reporting service authentication denied");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"AUTHENTICATION_REQUIRED\","
                    + "\"message\":\"Authorized reporting access is required.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private List<String> values(HttpServletRequest request, String name) {
        return request.getHeaders(name) == null
                ? List.of()
                : Collections.list(request.getHeaders(name));
    }

    private boolean matches(String supplied, String expected) {
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private boolean validBearer(String value) {
        return value.startsWith("Bearer ")
                && !value.substring("Bearer ".length()).isBlank();
    }
}
