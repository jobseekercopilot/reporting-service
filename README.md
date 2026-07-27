# Reporting Service

Spring Boot service containing the inherited Job Seeker Copilot reporting
calculations.

## Current scope

The service calculates an application-status summary, a recent-activity list, an
indicative commitment-progress value, and text currently named a UC journal. It is
not yet a complete beta-ready reporting product; the remaining product and data
coverage findings are recorded in
[`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md).

The inherited generated-client JAR dependencies have been replaced with
source-controlled HTTP adapters. Application Tracker requests use the Reporting
Service's dedicated reader identity and the trusted report owner. User Profile
requests forward the access token already validated by the Reporting Gateway and
use the authenticated `/api/profiles/me` boundary.

## Build

Java 17 and Maven are required.

```bash
mvn -B clean verify
```

The build is reproducible from source and does not require locally supplied JARs.

Runtime startup fails closed unless both credentials are present, at least 32
bytes long, and distinct:

- `REPORTING_GATEWAY_SERVICE_TOKEN`
- `APPLICATION_TRACKER_READER_TOKEN`

Reporting endpoints accept calls only from the Reporting Gateway. A call must
carry the exact gateway service token, one non-empty `X-Report-Owner` header, and
one bearer authorization value.

## API contract

The reviewed version 2 contract is in `contracts/openapi.json`. The test suite
fails if runtime-generated OpenAPI drifts from that file.

## Licence

Proprietary and confidential. See `LICENSE`.
