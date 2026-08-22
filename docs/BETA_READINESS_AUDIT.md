# Reporting Service beta-readiness audit

Audit date: 2026-07-23  
Decision: **Not ready for private beta**

This document records the inherited implementation without changing its product
behaviour. The repository was created as a sanitised audit baseline only.

## Post-audit implementation progress

The dated findings below remain the audit baseline rather than a description of
the current source tree. Subsequent scoped Stories replaced local client JARs
with authenticated source-controlled HTTP adapters, added persisted Application
Tracker history and Document Store activity, and made the build reproducible.

Feature `document-generation-gateway#38` / Reporting Story #18 additionally
projects the approved saved-application and document-choice meanings without
copying event payloads. It deduplicates event IDs across pages, rejects Tracker
records/events outside the trusted owner/application, ignores unknown event
types and fields, and redaction-tests document content, extracted text,
filenames, notes, hashes, scanner details, tokens and object locations. This
does not close the audit's separate date-range, terminology, user-control,
performance, commitment-metric or full E2E findings.

## Verified behaviour

- Fetches current applications from Application Tracker by user ID.
- Fetches profile context and defaults weekly target hours to 35 when unavailable.
- Counts applications by their current status and returns at most 10 timeline
  entries.
- Generates text named a UC journal and indicative commitment-progress hours.
- Propagates correlation IDs and exposes the Spring Boot health endpoint.
- Produces an OpenAPI document during tests.
- With the two inherited untracked client JARs present, `mvn -q clean verify`
  passed 5 tests (0 failures, 0 errors, 0 skipped).

## Clean-clone and supply-chain result

- The POM uses Maven `systemPath` for generated Application Tracker and User
  Profile client JARs in `libs`.
- Generated binaries are excluded from this repository by design.
- A clean clone therefore cannot compile or test.
- The Dockerfile also copies `libs`, so its build is not reproducible from this
  repository.
- The API contract is captured in `contracts/openapi.json`, but no reproducible
  client-generation and publication workflow is present.
- Application Tracker and Build Tools do not currently have the required private
  repository boundaries. They were audited as missing dependencies, not created
  because they are outside the approved primary-repository scope.

Unblock condition: establish approved source repositories and versioned contract
publication for dependencies, remove `systemPath`, and prove clean-clone CI.

## Accuracy and product findings

### Critical: untrusted user scope

The controller accepts `X-User-Id`; the service uses it in downstream requests and
does not derive it from validated authentication. Application Tracker also exposes
user-ID and object-ID operations without authenticated ownership enforcement.

Unblock condition: validate authentication at the edge, propagate a trusted subject,
enforce ownership in every source service, and prove cross-user isolation.

### Critical: current rows are not an activity history

Application Tracker stores a mutable current application row. This service invents
one timeline entry per row from `appliedAt`, `updatedAt`, or `createdAt`; status
changes, saved jobs, generated documents, notes, interviews, offers, and prior
events cannot be reconstructed reliably.

Unblock condition: define and persist an auditable activity/event source and
reconcile known-answer scenarios before presenting the timeline as evidence.

### High: metrics and date rules are undefined

- Counts represent current rows, not events within a selected period.
- No user-selected date range is supported.
- `LocalDate.now()` uses the server default zone; Europe/London boundaries, week
  rules, daylight-saving transitions, future/undated/backdated data, deletion, and
  duplicate handling are unspecified.
- No injected clock supports deterministic boundary tests.
- One calculation checks a `REJECTED` status not present in the inspected
  Application Tracker enum.
- Commitment hours are invented from status weights and must not be represented as
  verified work-search activity.

### High: evidence terminology and user control are unsafe

The API and DTOs call the text a UC journal. The current output is not an approved
Universal Credit or DWP report. It has no selected date range, preview choices,
notes opt-in, factual disclaimer, internal-ID policy, or report export.

Use the product term **user-controlled job-search evidence** unless formal approval
supports another description.

### High: incomplete source ownership

The implementation uses Application Tracker and User Profile only. It does not
query Job Service for jobs and sources, Document Store for exact document versions,
or Document Export for evidence export. Reporting must remain a deterministic
reader, not the owner of those domain records.

### High: performance and privacy controls are absent

Application data is loaded as an unpaginated collection and sorted in memory. No
large-user/range tests, query budgets, safe per-user cache policy, export resource
limits, privacy selection, redaction tests, or access-denied metrics are present.
Raw user IDs and counts are logged.

## Report inventory

| Capability | Result | Evidence |
|---|---|---|
| Applications by current status | Existing but incomplete | Deterministic for the supplied current rows; no date rules or ownership. |
| Recent activity | Existing but inaccurate for evidence | Reconstructed from current rows; limited to 10. |
| Commitment progress | Not justified | Invented status-to-hours weights, not verified activity. |
| UC journal text | Existing but unsafe terminology | Not approved or user-controlled evidence. |
| Jobs found/saved/by source | Required, absent | Job Service is not queried. |
| Documents generated/version used | Required, absent | Document Store is not queried. |
| Outstanding actions | Required, absent | No defined source. |
| Date-range evidence report | Required, absent | No date selection, review, or export. |
| Weekly/monthly trend | Post-beta unless justified | No historical event source. |
| AI narrative | Deferred | Core reporting must work without an LLM. |

## Required testing and operations

No integration, authentication, cross-user, date-boundary, known-answer,
pagination, performance, privacy-selection, export, or end-to-end browser tests
were found. System Data contains only the general `demo-ready-v1` state, not named
reporting scenarios with exact expected totals. E2E reporting coverage is
promotional/conditional rather than a strict product assertion.

The GitHub Reporting epic contains focused issues for these gaps. The epic remains
in Backlog and no issue was implemented during this audit.
