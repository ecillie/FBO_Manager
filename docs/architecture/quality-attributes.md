# MVP Quality Attributes

## 1. Purpose

This document turns general expectations such as “fast,” “reliable,” and “secure” into scenarios that can guide design reviews and acceptance tests. Targets are MVP baselines and should be revisited with pilot-airport measurements.

When attributes conflict, the priority order is:

1. safety and operational correctness;
2. security and auditability;
3. recoverability and availability;
4. performance and usability; and
5. implementation convenience.

## 2. Quality scenarios

| Attribute | Scenario and stimulus | MVP target | Verification approach |
| --- | --- | --- | --- |
| Consistency | Two dispatchers assign the same parking spot concurrently. | Exactly one assignment commits; the other receives a conflict with no partial visit change. | PostgreSQL concurrency integration test. |
| Consistency | Two dispatchers start tasks using the same worker or vehicle. | Exactly one task starts; the other remains unchanged and receives a conflict. | Concurrent service/API test. |
| Inventory correctness | Concurrent fuel movements target the same holder. | Every accepted movement appears exactly once and paired transfers commit both sides or neither; the estimated balance may be negative or exceed nominal capacity. | Concurrent ledger and rollback integration tests. |
| Idempotency | A client retries a fuel transfer or dispense after losing the response. | One inventory effect exists for the idempotency key and the retry returns the original outcome or an explicit conflict. | API and ledger-history test. |
| Read performance | A staff user loads a normal detail or bounded list under expected load. | 95th percentile below 500 ms at 25 concurrent users. | Repeatable load test against representative data. |
| Write performance | A staff user performs a normal operational write without lock contention. | 95th percentile below 1 second at 25 concurrent users. | Repeatable load test excluding artificial external latency. |
| Dashboard freshness | A ramp-board user monitors current operations. | The [ADR 0003](decisions/0003-api-contracts-and-operational-data-flows.md) polling baseline reflects committed state within 15 seconds. | End-to-end refresh test. |
| Availability | Staff use the shared MVP environment throughout airport operations. | 99.5% monthly availability excluding announced maintenance. | External health monitoring and monthly review. |
| Graceful failure | PostgreSQL is unavailable during a write. | The request fails safely, returns a traceable service error, and creates no partial application state. | Dependency-failure integration test. |
| Recovery | The primary MVP database becomes unrecoverable. | Restore service within four hours with no more than 24 hours of committed data loss. | Documented restore exercise using an automated daily backup. |
| Authentication | An unauthenticated or expired client calls a protected endpoint. | Access is denied with 401 and no protected data is returned. | API security test. |
| Authorization | An authenticated worker lacks a required capability. | Access is denied with 403; the attempted sensitive action is traceable. | Capability-matrix test. |
| Secret protection | An application error or log event contains sensitive context. | Credentials, tokens, secrets, raw SQL, and client-visible stack traces are never emitted. | Error-path tests plus log inspection. |
| Auditability | A user adjusts fuel, overrides dispatch, or changes security/configuration state. | Actor, UTC time, action, target, result, and supplied reason are retained. | Audit-event integration test and review query. |
| Maintainability | A developer creates a new environment from the repository. | Pinned dependencies and ordered migrations produce a working system from documented commands. | Clean-environment CI job. |
| Contract stability | Backend request or response behavior changes. | OpenAPI validation detects undocumented breaking changes before merge. | Contract lint/diff check in CI. |
| Time correctness | A schedule spans midnight or a daylight-saving transition in the airport timezone. | Stored UTC order remains correct and airport-local display/filter boundaries match the configured timezone. | Timezone-boundary tests. |
| Accessibility | A staff user operates core browser workflows with keyboard or assistive technology. | Core workflows meet WCAG 2.2 AA expectations selected by issue #30. | Automated checks plus manual keyboard/screen-reader review. |

## 3. Performance data profile

Performance verification should use data large enough to reveal query and pagination problems:

- 500 visits across the active verification day;
- at least one year of retained historical visits;
- 2,500 service requests and 5,000 tasks for the active day;
- 500 parking spots, workers, and vehicles where the domain permits;
- enough fuel transactions to exercise holder history and balance queries; and
- 50 simulated concurrent authenticated users for headroom testing.

Tests must report the PostgreSQL version, application version, environment shape, dataset size, percentile measured, and whether caches were warm. A single local response time is not performance evidence.

## 4. Reliability and recovery rules

- Critical multi-record writes use a database transaction owned by the application service.
- Expected contention produces a domain conflict, not an unhandled server error.
- The application does not claim success until the authoritative transaction commits.
- Retried sensitive writes use the persisted key and replay mechanism defined by [ADR 0003](decisions/0003-api-contracts-and-operational-data-flows.md).
- Health reporting separates process liveness from readiness to serve database-backed requests.
- Shutdown stops accepting new work, completes or cancels bounded in-flight work, and closes database connections.
- Hosted connections use TLS.
- Automated backups run daily, and a documented restore exercise verifies that backups are usable.

## 5. Security and audit baseline

- Every operational endpoint is authenticated unless explicitly documented as a health endpoint.
- Authorization decisions use server-side capabilities tied to an active worker identity.
- Clients cannot grant themselves roles, actor identifiers, or override authority.
- Credentials and signing material come from environment or managed secret injection, never committed files.
- Authentication attempts are rate-limited.
- Sensitive writes carry a request/correlation identifier and acting worker context.
- Ordinary diagnostic logs are not a substitute for immutable fuel history or required audit events.
- Security details and the complete threat model are owned by issue #33.

## 6. Acceptance and revision

Issue #28 accepts these targets as the planning baseline. A target may change after pilot measurement, but the change must document:

1. the evidence that invalidated the current target;
2. the operational impact of the new target;
3. the affected implementation and test issues; and
4. the approving architecture decision or review.

Unmeasured quality claims are treated as risks, not as completed requirements.
