# ADR 0003: API contracts and operational data flows

- **Status:** Accepted
- **Date:** 2026-08-09
- **Decision owners:** FBO Manager architecture contributors
- **Tracking issue:** [#32](https://github.com/ecillie/FBO_Manager/issues/32)
- **Related issues:** [#7](https://github.com/ecillie/FBO_Manager/issues/7), [#11](https://github.com/ecillie/FBO_Manager/issues/11), [#12](https://github.com/ecillie/FBO_Manager/issues/12), [#24](https://github.com/ecillie/FBO_Manager/issues/24), and [#25](https://github.com/ecillie/FBO_Manager/issues/25)

## Context

FBO Manager's browser and future clients need one predictable contract while the backend protects concurrency-sensitive operational state. The [MVP architecture](../../architecture.md), [workflow definitions](../mvp-scope-and-workflows.md), [frontend ADR](0001-frontend-application-architecture.md), [backend ADR](0002-backend-application-architecture.md), and [database design](../../database-design.md) already establish that:

- the backend and PostgreSQL are authoritative;
- parking, dispatch, visit, service, and fuel commands do not succeed before a transaction commits;
- generated IDs and fixed-precision quantities must survive a JavaScript client without precision loss;
- concurrent commands need one clear winner or deterministic serialization, never partial state;
- fuel ledger retries must not duplicate an inventory effect; and
- the operations dashboard must reflect committed state within 15 seconds.

Issues #12 and #24 need architectural decisions for route/version style, serialization, validation, response envelopes, collections, errors, idempotency, and current-state reads. The critical workflows also need explicit transaction boundaries and concurrency outcomes before controller and OpenAPI implementation begins.

## Decision

The controlling detailed contract is [API contracts and operational data flows](../api-contracts-and-data-flows.md). Its requirements are summarized here.

### Contract boundary

- Expose a synchronous resource-oriented HTTPS/JSON API under `/api/v1`.
- Use plural kebab-case paths, lower-camel-case JSON/query names, and upper-snake-case closed enum values.
- Use explicit subordinate `POST` commands for operational transitions that are not ordinary record replacement.
- Serialize instants as UTC RFC 3339 strings, calendar dates as `YYYY-MM-DD`, generated `BIGINT` IDs as decimal strings, and `NUMERIC(14,3)` quantities as three-decimal strings paired with controlled units.
- Keep backward-compatible additions in `v1`; publish a coexisting major path for breaking changes and enforce drift with the canonical OpenAPI artifact.

### Validation, collections, and envelopes

- Reject malformed, unknown, unbounded, or structurally invalid input before application commands run; repeat authoritative domain and concurrency checks in the application transaction.
- Bound every ordinary collection with zero-based page/size parameters, a maximum size of 100, allowlisted filters and sort fields, and a stable unique tie-breaker.
- Wrap JSON successes in `data` plus `meta`; use a stable error object containing HTTP status, application code, safe message, request ID, timestamp, retryability, and optional safe field/detail data.
- Map expected current-state, uniqueness, idempotency, and concurrency failures to `409`, domain compatibility failures to `422`, and dependency failures to `503` without exposing persistence details.

### Transactions and idempotency

- Keep command transaction ownership at public application-service methods and acquire deterministic PostgreSQL row locks before decisions on contested state.
- Create a field-service request and its linked pending task atomically. Dispatch/start claims worker and vehicle resources and starts the linked service atomically in a separate user-intent transaction.
- Arrive-and-park is one transaction. Tank-to-truck transfer writes both ledger entries and the transfer group in one transaction. Aircraft dispense writes one immutable truck entry linked to the service request and actor.
- Require `Idempotency-Key` for operational creates, state transitions, task dispatch/completion, and every fuel movement. Claim and complete the idempotency result in the same transaction as the business effect; matching retries replay and mismatched reuse conflicts.
- Retain fuel-operation idempotency evidence as long as its ledger history; retain other completed command outcomes for at least 24 hours.

### Current state and freshness

- Serve the ramp board from one purpose-built `/api/v1/operations/dashboard` query rather than client-side fan-out.
- Compose the projection from authoritative operational tables plus `worker_current_status`, `service_vehicle_current_status`, `fuel_tank_balances`, and `fuel_truck_balances` in one database snapshot. Do not create a writable duplicate dashboard model.
- Poll the visible operations dashboard every 10 seconds, pause when hidden, and refresh on focus, reconnect, and relevant successful mutations. SSE and WebSockets are not part of the MVP.

## Consequences

### Benefits

- Backend implementation, OpenAPI, generated frontend types, and future clients share exact primitive and envelope conventions.
- Explicit command routes express operational intent without exposing unrestricted persistence updates.
- Decimal strings and string IDs remove browser precision ambiguity.
- Idempotency plus database transactions makes response-loss retries safe for inventory and other sensitive writes.
- Sequence-level lock and rollback rules give API and concurrency tests observable outcomes.
- A snapshot dashboard avoids inconsistent client fan-out while reusing the authoritative database views.
- Polling meets the accepted freshness target without a message broker or long-lived connection infrastructure.

### Costs and risks

- Success envelopes and decimal/string mappings add DTO code and generated-type ceremony.
- Page totals and a composed dashboard query need representative PostgreSQL performance tests and indexes.
- Idempotency requires additional schema, retention, request fingerprinting, and replay behavior.
- Explicit workflow endpoints add contract surface and must remain aligned with application use cases.
- Polling repeats reads even when state is unchanged; bounds, visibility pausing, and focused invalidation limit that cost.
- Closed enums protect generated clients but require a major-version decision when a new value cannot be introduced compatibly.

## Rejected alternatives

| Alternative | Reason not selected for the MVP |
| --- | --- |
| Unversioned routes or version-only media types | A visible major path is simple for browser configuration, logs, OpenAPI artifacts, and parallel migration. Header-only negotiation adds tooling and support ambiguity. |
| GraphQL | The MVP workflows are command- and transaction-oriented, not an open-ended client graph. It would add schema, authorization, caching, error, and N+1 policy without removing backend use-case design. |
| gRPC as the client contract | Browser support and human-operable HTTP diagnostics would require additional translation. Protobuf is not needed for the MVP scale. |
| Strict JSON:API | Its generic relationship and error rules add ceremony while operational command endpoints and purpose-built dashboard projections still require project-specific semantics. |
| Bare success bodies and ad hoc errors | They make request correlation, pagination metadata, generated client handling, and safe error behavior inconsistent across modules. |
| JSON numbers for quantities or generated IDs | Binary floating point cannot represent fixed decimals exactly, and JavaScript numbers cannot preserve every PostgreSQL `BIGINT`. |
| Automatic mutation retries without server idempotency | Response loss could duplicate visits, tasks, transfers, dispenses, or adjustments. Explicit retries with persisted keys make intent and outcome observable. |
| Client fan-out for the authoritative ramp board | Independent requests can observe different commits, produce transient contradictions, and duplicate composition rules in every client. |
| Server-sent events or WebSockets for MVP current state | The 10-second polling design meets the 15-second target. Push would add connection lifecycle, authorization renewal, event ordering, cross-instance distribution, and recovery work without a measured need. |
| Asynchronous event choreography for critical flows | Arrival, dispatch, service/task creation, and paired fuel entries require atomic outcomes. Eventual consistency would expose prohibited partial states. |

## Compliance and revision

Issue #12 must implement the shared controller, validation, request-ID, pagination, idempotency-header, and error behaviors. Issue #24 must publish the exact schemas, examples, security declarations, error responses, and breaking-change checks in `contracts/openapi/v1.yaml`. Contract tests must prove that implementation and OpenAPI agree.

PostgreSQL concurrency tests must demonstrate the transaction outcomes shown in the detailed sequences: one parking winner, no partial service/task creation, no partial dispatch, atomic paired transfers, and one ledger effect per idempotency key. Dashboard tests must prove snapshot consistency, bounds, view composition, and the 15-second end-to-end freshness target.

A later decision may introduce cursor pagination for a new high-volume feed, a push transport, or a different envelope only with measured evidence and an explicit compatibility/migration plan. A breaking revision requires a superseding ADR and a new API major version.
