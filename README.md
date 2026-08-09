# Reporting Service

## Role in Job Seeker Copilot

| Role | Called by | Calls | Data | Local port |
|---|---|---|---|---:|
| Read-only application/document/profile projection for summaries, timelines and evidence | Reporting Gateway | Application Tracker, Document Store, User Profile | None | 8096 |

Reporting estimates commitment hours from application statuses; it is not an official submission or measured time log. See the central [reporting journey](https://docs.jobseekercopilot.com/journeys/reporting-payments/) and [data ownership](https://docs.jobseekercopilot.com/data/ownership/).

Spring Boot service containing the inherited Job Seeker Copilot reporting
calculations.

## Current scope

The service calculates an application-status summary, a recent-activity list, an
indicative commitment-progress value, and text currently named a UC journal. It is
not yet a complete beta-ready reporting product; the remaining product and data
coverage findings are recorded in
[`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md).

The inherited generated-client JAR dependencies have been replaced with
source-controlled HTTP adapters. Application Tracker and Document Store
requests use the Reporting Service's dedicated reader identities and the trusted
report owner. User Profile
requests forward the access token already validated by the Reporting Gateway and
use the authenticated `/api/profiles/me` boundary.

Recent activity merges ordered Application Tracker history with the Document
Store's content-free lifecycle feed. It reports safe user-facing document
version actions without document content, file names, hashes, scanner details,
evidence or notes. Reporting remains a read-only projection and never decides
document or application state.

The application-centred document journey reports these approved content-free
activity meanings:

- `APPLICATION_SAVED`
- `APPLICATION_DOCUMENT_PLAN_SELECTED`
- `DOCUMENT_UPLOADED`
- `DOCUMENT_VERSION_CREATED`
- `DOCUMENT_LINKED_TO_APPLICATION`
- `DOCUMENT_REPLACED`
- `DOCUMENT_DELETED`

Tracker selection events are normalised into plan, link and replacement
meanings only from their bounded content-free state-change description. Event
IDs are deduplicated across pages, Tracker records and events must match the
trusted report owner/application, and unknown event types or fields are ignored
instead of being copied into user evidence. The Document Store feed remains
owner-scoped by its dedicated reader identity; an upload is reported only when
its source is explicitly `UPLOADED`.

## Build

Java 17 and Maven are required.

```bash
mvn -B clean verify
```

The build is reproducible from source and does not require locally supplied JARs.
It also writes a CycloneDX 1.6 runtime SBOM to
`target/classes/META-INF/sbom/application.cdx.json` and packages it in the
application JAR.

The complete local dependency-security gate is:

```bash
./scripts/verify-supply-chain.sh
```

The gate uses a pinned, free local Trivy container and never exposes the Docker
socket to the scanner. See
[`docs/SECURITY_VERIFICATION.md`](docs/SECURITY_VERIFICATION.md) for the dated
results, offline mode and remaining image/TLS evidence.

Build and scan the complete local image through the least-privilege archive
boundary:

```bash
docker build --tag jobseekercopilot/reporting-service:local .
REPORTING_TRIVY_OFFLINE=true \
  ./scripts/verify-image-security.sh jobseekercopilot/reporting-service:local
```

Runtime startup fails closed unless all credentials are present, at least 32
bytes long, and pairwise distinct:

- `REPORTING_GATEWAY_SERVICE_TOKEN`
- `APPLICATION_TRACKER_READER_TOKEN`
- `DOCUMENT_STORE_READER_TOKEN`

Reporting endpoints accept calls only from the Reporting Gateway. A call must
carry the exact gateway service token, one non-empty `X-Report-Owner` header, and
one bearer authorization value.

Runtime OpenAPI and Swagger UI endpoints are disabled by default. The reviewed
contract remains available in source; operators may enable the runtime endpoints
explicitly with `OPENAPI_DOCS_ENABLED=true` or `SWAGGER_UI_ENABLED=true`.

## API contract

The reviewed version 2.1 contract is in `contracts/openapi.json`. The test suite
fails if runtime-generated OpenAPI drifts from that file.

## Licence

Proprietary and confidential. See `LICENSE`.
