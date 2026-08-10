# Observability, Reliability, Audit, and Recovery

## 1. Purpose and scope

This document defines the MVP signals, alerts, audit separation, failure handling, backup, restore, and recovery responsibilities for GitHub issue [#35](https://github.com/ecillie/FBO_Manager/issues/35). The controlling decision is [ADR 0006](decisions/0006-managed-telemetry-and-tested-backup-recovery.md). Backend operating documentation issue [#27](https://github.com/ecillie/FBO_Manager/issues/27) must turn these requirements into provider-specific commands and runbooks.

The MVP optimizes for detection, diagnosis, safe failure, and tested restore. It does not claim automatic failover, point-in-time recovery, or uninterrupted operation during an application or database outage.

## 2. Signal model and ownership

FBO Manager emits three different types of operational evidence:

1. **Diagnostic telemetry** consists of structured logs, metrics, and optional sampled traces used to understand software and infrastructure behavior. It is useful but not an authoritative business record.
2. **Security/application audit events** are append-only records of who attempted or completed a sensitive action, as defined in the [security architecture](security.md). They have explicit transaction and retention requirements.
3. **Domain history** is authoritative application data such as aircraft visits, completed tasks/shifts, and the immutable fuel inventory ledger. It is never reconstructed from logs or audit events.

| Owner role | Responsibility |
| --- | --- |
| Application owner | Safe instrumentation, dashboards, error classification, runbooks for application failures, and release correlation. |
| Platform on-call owner | Environment health, TLS/DNS, container capacity, telemetry collection, backup job monitoring, and first response to pages. |
| Database owner | PostgreSQL availability/capacity, slow-query review, migrations, backup integrity, restore execution, and data-recovery decisions. |
| Security owner | Authentication/authorization alert review, audit access, suspected credential/session compromise, and incident evidence. |
| FBO operations manager | Business impact assessment, staff communication, manual operating procedure, inventory reconciliation, and acceptance after restore. |
| Release owner | Post-deploy observation, rollback/forward-fix coordination, and release annotation in telemetry/audit systems. |

One named person may hold several roles for the MVP, but every shared environment records a primary and backup contact. An alert without an owner and response runbook is not actionable and must not be configured as a page.

## 3. Structured diagnostic logs

The backend writes one JSON object per event to standard output. The platform collector adds infrastructure metadata and transports it over an encrypted provider channel. Field names and types remain stable enough for saved queries.

### 3.1 Required fields

| Field | Requirement |
| --- | --- |
| `timestamp` | RFC 3339 UTC instant with millisecond or finer precision. |
| `level` | Controlled severity: `DEBUG`, `INFO`, `WARN`, or `ERROR`. Production defaults to `INFO`. |
| `service`, `environment`, `version`, `instanceId` | Identifies the immutable artifact and runtime. `instanceId` is platform-generated, not a hostname containing sensitive data. |
| `event` | Stable low-cardinality event name such as `http.request.completed` or `task.dispatch.conflict`. |
| `message` | Short static human explanation; variable values belong in fields. |
| `requestId`, `traceId` | Request/correlation IDs when available. Preserve a validated inbound request ID and generate one otherwise. |
| `actorWorkerId` | Authenticated server-derived worker ID when relevant. Omit when unknown; never accept it from a client field/header. |
| `httpMethod`, `route`, `status`, `durationMs` | Use the route template, not the raw path/query string. Record only at request completion. |
| `outcome`, `errorCode` | Controlled success/failure classification and safe application error code. |
| `dependency`, `operation` | Safe low-cardinality database/provider/backup identifiers for dependency events. |

Exception type and a sanitized stack trace may be retained for unexpected server failures in the restricted log store. Expected validation, authorization, not-found, and conflict results do not need stack traces. SQL text is represented by an approved query name or normalized fingerprint, never raw bound values.

### 3.2 Redaction and volume rules

Logs must not contain authorization/cookie headers, OIDC tokens or claims, session/CSRF/idempotency values, passwords or client secrets, complete database URLs, raw SQL parameters, request/response bodies, contact information, notes/free text, tail-number search queries, or backup contents. Headers and business fields are denied by default; safe fields are allowlisted. New structured fields receive privacy/security review.

Do not log every dashboard row, entity, SQL call, or health success. Sample optional success detail while retaining complete request aggregate metrics and all warnings/errors. Rate-limit repeated identical errors to protect the collector while incrementing an unsampled counter. Collector failure must not block or crash a committed business transaction; bounded buffers drop diagnostic events with an observable counter rather than exhausting memory/disk.

Production diagnostic logs are retained searchable for 30 days and then deleted under provider policy. NonProd logs are retained 14 days; Development/CI/local retention is transient. Extending retention requires a purpose and privacy/cost review. Audit events and domain history follow their separate policies.

## 4. Metrics and dashboards

The backend exposes private Micrometer metrics and exports them through OpenTelemetry Protocol (OTLP) or the platform's compatible managed collector. Metric labels must be low-cardinality: never worker IDs, customer IDs, tail numbers, request IDs, raw paths, task/visit IDs, tank/truck names, exception messages, or SQL text.

### 4.1 Required service and database metrics

| Area | Required metrics/views |
| --- | --- |
| HTTP service | Request rate, in-flight requests, status/error-code class, p50/p95/p99 duration by route template and method, rate-limit decisions, request-size rejection, liveness/readiness. |
| JVM/process | CPU, resident memory, heap/non-heap usage, garbage-collection pause, thread count, file descriptors where available, restarts, uptime, graceful-shutdown timeout. |
| Database client | Hikari active/idle/pending/max connections, acquisition duration/timeouts, transaction duration, lock/deadlock/statement timeout counts, migration version. |
| PostgreSQL service | Availability, connections versus cap, CPU, memory/cache pressure where exposed, storage use/growth, I/O latency, replication status only when later applicable, deadlocks, long transactions, backup age/status. |
| Identity | Login success/failure/denial counts by safe reason, OIDC dependency duration/error, active/revoked session counts. Never use subject/account as a metric label. |

### 4.2 Workflow and fuel metrics

| Area | Required metrics/views |
| --- | --- |
| Parking | Arrival/parking command success, preference override, occupied/conflict, lock timeout, and transaction rollback counts. |
| Task dispatch | Dispatch/start/complete success, worker conflict, vehicle conflict, stale state, and rollback counts; pending/in-progress task gauges from bounded operational queries. |
| Visits/services | State-transition counts and failures by controlled transition; active visit and overdue service/task gauges. |
| Fuel inventory | Receipt/transfer/dispense/adjustment counts, paired-transfer rollback, idempotency replay/conflict, holder balance outside nominal range, and unreconciled adjustment count. Quantities and holder identifiers stay in authorized operational views, not metric labels. |
| Current-state freshness | Dashboard query duration/error, returned-section truncation, client-reported refresh age in end-to-end checks, and last successful synthetic read. |

Fuel metrics are detection prompts, not a second ledger. An out-of-range estimated balance is visible for reconciliation and does not by itself reject the transaction. The FBO fuel manager reviews the authoritative ledger and records any correction as a compensating adjustment with reason.

## 5. Alert catalog and response expectations

Thresholds are starting values. Tune them using pilot evidence while preserving the quality targets; record changes with the runbook.

| Alert | Initial trigger | Owner and response |
| --- | --- | --- |
| Public application unavailable | Three consecutive one-minute synthetic HTTPS/readiness failures or readiness false for 5 minutes | Platform on-call acknowledges within 15 minutes during the FBO's staffed operating window, checks edge/app/database, and informs operations manager if impact persists 15 minutes. |
| Elevated server errors | `5xx` above 5% with at least 20 requests over 5 minutes | Application owner; correlate release/dependency, halt promotion, choose compatible rollback or forward fix. |
| Latency target breach | p95 normal reads above 500 ms or writes above 1 s for 15 minutes at meaningful volume | Application + database owner within one business hour; inspect pool, locks, slow fingerprints, and capacity. Page only if paired with user-visible failure. |
| Database unavailable / pool exhausted | Database health fails, acquisition timeouts occur, or pending pool requests persist above zero for 5 minutes | Platform/database owner immediately; keep API unready for unsafe operation, protect data, and invoke outage/recovery runbook. |
| Storage pressure | PostgreSQL storage above 70% warning or 85% critical, or forecast under seven days | Database owner: assess growth/retention at warning; page and expand safely at critical. Never delete authoritative history ad hoc. |
| Backup missing or failed | No successful production backup in 30 hours, failed job, or retention/encryption validation failure | Database owner acknowledges within 30 minutes, restores backup coverage, and records gap. Escalate to operations manager if RPO is at risk. |
| Restore exercise overdue | No successful production-like restore test in the prior 100 days | Database and release owners block Release One readiness and schedule the exercise within seven days. |
| Authentication anomaly | Failure rate exceeds normal baseline and either 50 failures/5 minutes or repeated rate limits; provider failures above 20%/5 minutes | Security owner within 30 minutes; distinguish provider outage from attack, preserve evidence, tighten access/revoke sessions if needed. |
| Required audit persistence failure | Any sensitive command cannot persist its required audit event or audit writer errors | Application/security owner immediately; fail the command closed, halt affected releases/workflows, restore audit capability. |
| Parking/dispatch conflicts spike | Conflict or lock-timeout rate exceeds 10% of attempts for 15 minutes | Application + operations manager within one business hour; check contention/UI freshness. Safety constraints remain enabled. |
| Fuel anomaly | Paired-transfer rollback occurs, balance crosses nominal bound, or adjustment rate exceeds site threshold | Fuel manager before the next fuel operation/shift handoff; reconcile physical state and ledger. Page only for suspected loss or unsafe operation. |
| Crash loop/resource saturation | More than 3 restarts/10 minutes, memory above 90%, CPU saturation for 15 minutes, or graceful drain timeout | Platform/application owner; stop repeated unsafe rollout, collect safe diagnostics, and restore last compatible artifact or resource headroom. |

Alerts route through the managed monitoring service to environment-specific groups. Production pages reach only named on-call contacts; Development/NonProd warnings go to the developer during working hours. Alert messages contain environment, service, safe symptom, dashboard/runbook link, and release version—never raw customer/worker/inventory data.

## 6. Audit records versus logs

The `audit_events` store is part of PostgreSQL and writable only through the audit application component. Required successful business audit events commit atomically with their changes. Failed authentication/authorization attempts use a separate bounded audit transaction. The application account cannot update/delete audit rows through ordinary repositories; database owner break-glass operations are provider-audited.

Audit events retain at least 365 days online for the MVP. Daily database backups include them. Access requires `AUDIT_READ` plus event-category scoping, and every search/export is itself audited. Diagnostic log deletion does not delete audit evidence. The fuel ledger, visit/task/shift history, and idempotency evidence remain authoritative domain records under their own retention rules.

## 7. Partial-failure behavior

| Workflow | Failure handling |
| --- | --- |
| Aircraft arrival and parking | Lock the visit and target spot in deterministic order. Visit transition, spot assignment, idempotency outcome, and audit event commit together. Occupied/stale state returns `409`; dependency/timeout rolls back all changes. A lost response is retried with the same idempotency key. No “occupied” flag is repaired separately because occupancy is derived. |
| Task dispatch/start | Lock task, worker, and vehicle deterministically. Assignment, task/service transition, idempotency outcome, and audit commit together. A worker/vehicle conflict leaves the task unchanged. The client refreshes authoritative state rather than guessing which assignment won. |
| Paired tank-to-truck transfer | Write equal-and-opposite ledger entries, shared transfer group, idempotency outcome, and audit event in one transaction. Any validation/database/audit failure writes neither entry. A response-loss retry replays the committed outcome. |
| Aircraft fuel dispense | Store one immutable truck ledger entry linked to service request and acting worker in the command transaction. If physical movement occurred but no record committed, an authorized fuel manager reconciles and records a compensating adjustment with required reason; history is never edited. |
| External identity provider | New login can fail with a safe unavailable response. Existing valid local sessions continue until local expiration/revocation because ordinary API requests do not call the provider. No login callback is partially accepted. |
| Telemetry collector | Bounded diagnostic buffers may drop logs/traces and increment a loss metric; business transactions continue. Required audit persistence is not diagnostic telemetry and fails sensitive commands closed. |

The backend never reports success before PostgreSQL commits. It does not automatically retry a mutation at HTTP level. A bounded database deadlock retry is permitted only inside the application service when the entire idempotent transaction is replayed and the deadline remains safe; implementation and tests must make that behavior explicit.

## 8. Slow-query and performance operations

Enable `pg_stat_statements` or the managed provider's equivalent without capturing bound values. Give every application query/use case a stable low-cardinality name. In NonProd and production:

- record a sanitized slow-query event for requests whose database time exceeds 250 ms, including query fingerprint/name, duration, rows, request ID, and transaction/use-case name;
- review the highest total-time, mean-time, p95 where available, call-count, lock-wait, and temporary-I/O fingerprints at least weekly during the pilot and before a release with query/schema changes;
- alert on the user-visible p95 targets in the quality attributes rather than paging on one slow query;
- investigate transactions open longer than 5 seconds and terminate only through an approved database runbook; and
- run the documented representative profile: up to 500 visits, 2,500 service requests, 5,000 tasks for the active day, 500 relevant resources, historical data, fuel history, and 50 simulated authenticated users.

Normal reads target p95 below 500 ms and writes below 1 second. Dashboard composition is one snapshot query path, bounded and N+1-free. Performance evidence records PostgreSQL/app versions, artifact digest, resource shape, data profile, percentile/sample volume, and cache state.

## 9. Backup, restore, and disaster recovery

### 9.1 Backup policy

| Control | MVP requirement |
| --- | --- |
| Frequency | Automated production database backup at least every 24 hours, scheduled outside the airport's expected peak when possible. Also take/verify an on-demand backup before a high-risk migration. |
| Retention | Keep 35 daily recovery points. Keep the final successful backup of each month for 12 months when the provider supports retention tiers; otherwise export that monthly backup to the approved encrypted backup store. |
| Protection | Provider-managed encryption at rest and in transit, a backup destination/account isolated from the application workload, least-privilege delete/restore access, and alerts on failure/age. Backup credentials are not application credentials. |
| Scope | PostgreSQL database including migration history, operational/domain data, sessions/idempotency records, and audit events. Immutable images, SBOMs, configuration definitions, and migrations are recovered from the artifact registry and repository, not database backup. Secrets are recovered/rotated through the secret manager. |
| Validation | Automated job success and backup metadata are necessary but not sufficient. Restore to an isolated non-production PostgreSQL 18 target at least quarterly and before a major PostgreSQL/platform change. |

### 9.2 Recovery objectives and accepted limits

The MVP recovery point objective (RPO) is 24 hours and recovery time objective (RTO) is four hours from declared database disaster to a verified application service. A disaster just before the daily backup may lose up to 24 hours of committed data. Restore, artifact deployment, secret/config validation, DNS/routing if needed, schema verification, and smoke testing must fit within four hours.

These are accepted pilot limits, not Release One targets. The MVP has no guaranteed point-in-time recovery, automatic database failover, multi-zone application availability, or zero data loss. The FBO operations manager must approve these limits and maintain a manual continuity process for aircraft, dispatch, and fuel activity during outage. Activity captured manually is entered/reconciled after recovery with explicit times, actors, and reasons; it is not silently backdated or inserted by direct SQL.

### 9.3 Restore exercise and incident sequence

The database owner leads; the platform owner provisions the isolated/replacement service; the application owner verifies schema and app behavior; the security owner controls access and rotates exposed secrets; the FBO operations manager validates business state and authorizes return to use.

1. Declare scope, stop writes or keep the API unready, preserve diagnostics, and select a recovery point. Do not overwrite the only copy of a failed database.
2. Provision an isolated PostgreSQL 18 target and restore with the provider-supported verified process. Record start/end, backup identifier/time, participants, and failures.
3. Validate migration checksums/version, constraints/views/triggers, row-count ranges, latest expected timestamps, audit continuity, fuel ledger pairing/balances, and database ownership/privileges.
4. Deploy the previously approved API/web digests with fresh or recovered configuration. Rotate database/session/OIDC secrets when compromise is possible; old sessions can be globally revoked.
5. Run authentication plus read/write smoke tests for arrival/parking, task dispatch, and a reversible test-domain fuel workflow in a designated test context. Confirm telemetry/backup coverage before routing users.
6. The operations manager compares the backup time with manual outage records, approves return, and oversees authorized reconciliation. Close with measured RPO/RTO, evidence, gaps, and assigned improvements.

A quarterly exercise succeeds only if a fresh isolated database becomes usable, integrity checks and critical smoke tests pass, and measured time meets the four-hour RTO. Merely listing or downloading a backup is not a restore test.

## 10. Telemetry location, access, and incident handling

Shared-environment JSON logs and OTLP metrics live in the managed telemetry service selected with the deployment provider; database metrics/backups live in the managed PostgreSQL/backup controls and feed summary alerts into the same operations view. Development, NonProd, and production use separate projects/indexes and access groups.

Access is named, MFA-protected, least privilege, and reviewed at least quarterly. The developer receives Development/NonProd access by default, not standing production access. Production diagnostic access is limited to application/platform support; audit access additionally requires `AUDIT_READ` or break-glass approval. Provider access and audit exports are logged. Shared links and local bulk downloads of production logs are prohibited. Incident evidence exports go to an approved encrypted location with a documented owner and deletion date.

Issue #27 must document exact dashboards, alert routes, provider backup commands, restore commands, manual-continuity contacts, reconciliation steps, common failure modes, and escalation contacts. No runbook may require a secret in a command line, committed file, ticket, or chat message.
