# Reporting supply-chain and runtime security evidence

This runbook records the local REPORT-14 controls. It intentionally uses no
GitHub Actions, cloud environment or paid provider.

## Repeatable local gate

Run:

```bash
./scripts/verify-supply-chain.sh
```

The gate:

1. runs the complete Maven verification;
2. generates a CycloneDX 1.6 runtime SBOM at
   `target/classes/META-INF/sbom/application.cdx.json` and packages it in the
   application JAR, with its SHA-256 digest in
   `target/security/sbom.sha256`;
3. copies the resolved runtime dependency JARs;
4. writes the complete Trivy JSON result to
   `target/security/reports/trivy-dependencies.json`; and
5. fails when Trivy finds a Critical or High dependency vulnerability.

The pinned scanner is `aquasec/trivy:0.72.0`. Its container receives only
read-only dependency JARs, a report directory and a dedicated cache directory.
It never receives the Docker socket. The first run may download the public
scanner image and vulnerability database; this does not invoke GitHub Actions
or a paid provider.

After a database has been cached, run without network access:

```bash
REPORTING_TRIVY_OFFLINE=true ./scripts/verify-supply-chain.sh
```

Build the image and pass its local reference to:

```bash
REPORTING_TRIVY_OFFLINE=true \
  ./scripts/verify-image-security.sh jobseekercopilot/reporting-service:local
```

This exports the exact local image to a temporary archive. Trivy receives that
archive read-only and never receives the Docker socket. The gate records all
findings and fails on any Critical or High image finding.

## Dated triage

Local scans used Trivy 0.72.0 with its 27 July 2026 vulnerability database:

| Component | Initial resolved findings | After Spring upgrade | Final patched runtime |
| --- | --- | --- | --- |
| Reporting Service | 4 Critical, 28 High, 22 Medium, 12 Low | 0 Critical, 0 High, 4 Medium | 0 findings |
| Reporting Gateway | 6 Critical, 32 High, 25 Medium, 12 Low | 0 Critical, 0 High, 4 Medium | 0 findings |

The remediation upgraded Spring Boot from 3.2.0 to 3.5.16 and springdoc from
2.3.0 to 2.8.17. The four remaining Medium findings have published fixes in
Jackson Databind 2.21.5 and Commons Lang 3.18.0; those managed patch versions
are pinned. The final reports contain zero dependency vulnerabilities at any
severity. The complete tests and clean-source Docker builds passed after the
framework upgrade. Full-history Gitleaks 8.30.1 scans covered all three commits
in each repository and found no leaks.

The first complete image scans found five High and ten Medium Alpine package
findings in each image. The fixed versions were available in the Alpine 3.23
repositories, so the runtime build now upgrades installed base packages before
adding `curl`. The rebuilt images' final reports contain zero Alpine and zero
application-JAR findings at any severity across both repositories.

## Personal-data and logging review

The 27 July 2026 tracked-source scan checked email addresses, UK National
Insurance number shapes and UK mobile-number shapes outside `target`. It found
no production value and one reserved `user@example.test` URL in a Gateway
configuration test. Test identities (`subject-123`, `alice` and `victim`) and
credentials are visibly synthetic.

Every production logging call site was also reviewed. Logs contain only bounded
service, method, path, status, duration, count, boolean-presence and generic
failure-category fields. They do not log report owner/user ID, bearer or service
tokens, profile content, application content, journal text, email or postcode.
Gitleaks supplies the separate complete-history credential scan.

## Runtime controls and residual evidence

- Reporting responses explicitly use `Cache-Control: no-store` and
  `X-Content-Type-Options: nosniff`.
- Runtime OpenAPI and Swagger UI are disabled by default.
- Images run as the unprivileged `application` user (uid 100) and define a
  health check.
- Gateway identity tests cover missing, expired, forged, wrong-issuer,
  wrong-audience, wrong-token-type and blank-subject tokens.
- Service and gateway tests prove caller-supplied ownership cannot override the
  authenticated subject.

The following evidence is still required before REPORT-14 can close:

- deployed HTTPS/TLS validation at the intended AWS ingress and service
  boundaries; and
- completion or explicit beta-risk acceptance for the report/export
  authorization work tracked by REPORT-03.

Do not treat the dependency-JAR scan as a full container-image scan; use the
separate archive-based image gate.
