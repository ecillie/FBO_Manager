# ADR 0006: Managed telemetry and tested backup recovery

- **Status:** Accepted
- **Date:** 2026-08-09
- **Decision owners:** FBO Manager architecture contributors
- **Tracking issue:** [#35](https://github.com/ecillie/FBO_Manager/issues/35)
- **Implementation issue:** [#27](https://github.com/ecillie/FBO_Manager/issues/27)

## Context

The MVP must detect user-visible failures, diagnose contention and database behavior, preserve security and inventory accountability, and recover from loss of its authoritative PostgreSQL database. A single-airport pilot cannot justify operating a full telemetry cluster or multi-zone database, but unmonitored containers and untested backups would not meet the accepted quality attributes.

Diagnostic logs have different integrity, privacy, transaction, and retention needs from audit events and domain history. Treating logs as the fuel ledger or required audit store would make correctness depend on a best-effort collector. Conversely, synchronously coupling business commits to external logging would turn telemetry outages into operational outages.

## Decision

Emit allowlisted JSON logs to standard output and low-cardinality Micrometer metrics through OTLP or a provider-compatible collector. Store/search them in environment-isolated managed telemetry with named MFA-protected access. Use request/trace IDs and server-derived worker context, never secrets, bodies, tokens, contact data, raw query values, or high-cardinality entity labels. Retain production diagnostic logs for 30 days and NonProd logs for 14 days.

Persist required audit events in PostgreSQL as append-only application records separate from logs. Commit successful sensitive-action audit events with the business transaction and fail that command closed if the audit event cannot persist. Retain audit events online at least 365 days. Keep the fuel ledger and other domain history authoritative and independent of both telemetry types.

Operate service, JVM, pool/database, authentication, workflow, dashboard, and fuel-safety metrics with a small actionable alert catalog. Every alert names an application, platform, database, security, release, or FBO operations owner and a response expectation. Use managed PostgreSQL query statistics with sanitized fingerprints and review slow/expensive queries against the accepted p95 targets and representative data profile.

Create encrypted automated database backups at least daily, retain 35 daily points and 12 monthly points where supported, and restore to an isolated PostgreSQL 18 target at least quarterly. The MVP accepts a 24-hour RPO and four-hour RTO. Database, platform, application, security, and FBO operations owners jointly verify schema, data, critical workflows, telemetry, and reconciliation before returning service.

The normative fields, metrics, alerts, partial-failure behavior, query thresholds, backup policy, restore sequence, and access model are in [Observability, reliability, audit, and recovery](../operations-and-recovery.md).

## Consequences

### Benefits

- Managed collection and alerting make the small team responsible for signals and response rather than telemetry-cluster maintenance.
- Stable request, release, dependency, and actor context supports diagnosis without logging protected payloads.
- Audit and fuel correctness do not depend on a lossy diagnostic pipeline.
- Actionable alerts and named owners reduce alarm noise and ambiguous response.
- Quarterly isolated restores prove that backup data, privileges, artifacts, configuration, and runbooks can recover a usable system.

### Costs and risks

- Managed telemetry and backup services add provider cost and require access/retention configuration.
- Redaction and low-cardinality discipline require tests and review whenever instrumentation changes.
- The four-hour RTO depends on trained humans and current runbooks; exercises consume time.
- Daily backups allow up to 24 hours of loss, and one primary permits an outage until restore. These are explicit MVP limits.
- A collector outage can lose diagnostic detail, while audit-write failure deliberately blocks affected sensitive commands.

## Rejected alternatives

| Alternative | Reason not selected for the MVP |
| --- | --- |
| Self-hosted logging/metrics cluster | Operating, securing, backing up, and upgrading another distributed data platform is disproportionate to one MVP application. |
| Plain-text unstructured logs | They make reliable correlation, redaction, saved queries, and alert fields difficult. |
| Log every request/response body or SQL value | It creates unnecessary credential, personal-data, and operational-data exposure and high storage/cardinality cost. |
| Use diagnostic logs as audit records | Logs are best effort, externally collected, shorter lived, and not transactionally coupled to sensitive writes. |
| Treat audit events as the domain ledger | Audit context does not replace authoritative visits, tasks, shifts, or paired immutable fuel transactions. |
| Metrics labeled by worker/entity/request identifiers | High cardinality harms cost and reliability and would expose sensitive operational identifiers. |
| Backup success notifications without restore tests | A present backup may still be unusable, incomplete, too slow, or inaccessible with current credentials/runbooks. |
| Immediate multi-zone HA/PITR | Release One owns those tighter objectives. Daily tested backups satisfy the explicitly accepted MVP RPO/RTO. |

## Compliance and revision

Issue #27 documents the concrete provider, dashboards, routes, commands, contacts, and runbooks. Backend test issue #25 verifies required audit transaction behavior and critical partial failures; CI/release strategy verifies migrations and restore-sensitive artifacts.

Changing the diagnostic/audit/domain separation, production retention, required-audit failure semantics, backup frequency, RPO/RTO, or recovery topology requires architecture and security review and, where it changes a major choice, a superseding ADR.
