# Reporting Service

Spring Boot service containing the inherited Job Seeker Copilot reporting
calculations.

## Current scope

The baseline calculates an application-status summary, a recent-activity list, an
indicative commitment-progress value, and text currently named a UC journal. It is
an audit baseline, not a beta-ready reporting product.

The source depends on locally supplied generated clients for Application Tracker
and User Profile. Those binaries are intentionally not committed. See
[`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md) for the verified
limitations and findings.

## Build

Java 17 and Maven are required.

```bash
mvn -B clean verify
```

The command will fail in a clean clone until generated-client dependencies are
made reproducible. Do not commit generated JARs as a workaround.

## API contract

The contract captured during the source audit is in `contracts/openapi.json`.

## Licence

Proprietary and confidential. See `LICENSE`.
