# Architecture Decision Record Index

## Purpose

This directory contains the accepted decisions that control the FBO Manager MVP. Detailed guides hold operational requirements and examples; ADRs explain why a major choice was made, its consequences, and which alternatives were rejected.

## Decision index

| ADR | Status | Context | Selected choice | Principal consequences | Superseded by |
| --- | --- | --- | --- | --- | --- |
| [0001: Frontend application architecture and state boundaries](0001-frontend-application-architecture.md) | Accepted | One browser UI must preserve domain authority, freshness, accessibility, and maintainable feature boundaries. | React/TypeScript SPA with feature modules, generated API types, TanStack Query server state, local UI/form state, and 10-second visible-page polling. | Clear state ownership and accessible responsive baseline; frontend dependency/tooling and polling discipline are required. | — |
| [0002: Backend application architecture and dependency boundaries](0002-backend-application-architecture.md) | Accepted | Critical PostgreSQL workflows need explicit transaction ownership without distributed-system overhead or framework leakage. | Java 25/Spring Boot 4.1 synchronous modular monolith with Spring Modulith, application-service transactions, ports/adapters, Flyway, JPA plus `JdbcClient`, and automated boundary rules. | Strong transaction/module boundaries and one deployable API; requires architectural tests and careful persistence mapping. | — |
| [0003: API contracts and operational data flows](0003-api-contracts-and-operational-data-flows.md) | Accepted | Browser/future clients need one precise contract while concurrency-sensitive commands remain atomic and retry-safe. | `/api/v1` HTTPS/JSON REST, stable envelopes/errors, decimal-string quantities/string IDs, explicit commands, persisted idempotency, snapshot dashboard, and polling. | Predictable OpenAPI/client behavior and safe retry/concurrency outcomes; adds DTO, idempotency, and query-verification work. | — |
| [0004: Delegated identity and capability-based authorization](0004-delegated-identity-and-capability-authorization.md) | Accepted | Staff access needs MFA/account governance, active-worker mapping, immediate local revocation, and accountable least privilege. | Delegated OIDC login; exact issuer/subject link; backend-owned opaque PostgreSQL session in a host-only HttpOnly cookie; live capability authorization; audited one-time admin bootstrap. | No local passwords/browser bearer tokens and immediate local role/revocation behavior; adds IdP configuration, session state, CSRF, and bootstrap operations. | — |
| [0005: Portable single-region container deployment](0005-portable-single-region-container-deployment.md) | Accepted | The MVP needs reproducible environments and operable deployment without premature cluster complexity. | Same-origin immutable web/API OCI images, one pre-deploy migration job, managed PostgreSQL 18, managed container platform, private data/management paths, and runtime secret injection. | Portable low-operations topology with clear migration ownership; one-instance/one-primary failure domains and brief maintenance are accepted. | — |
| [0006: Managed telemetry and tested backup recovery](0006-managed-telemetry-and-tested-backup-recovery.md) | Accepted | A small team must detect/diagnose failures, preserve audit integrity, and prove database recovery. | Redacted JSON logs and low-cardinality metrics in managed telemetry; PostgreSQL audit records separate from logs/domain history; daily encrypted backups and quarterly restores. | Actionable observability and tested 24-hour RPO/four-hour RTO; managed-service cost, manual exercises, and daily-backup loss window remain. | — |
| [0007: Layered verification and immutable artifact promotion](0007-layered-verification-and-immutable-promotion.md) | Accepted | PostgreSQL concurrency, API/security contracts, browser behavior, migrations, and supply chain all need proportional verification. | Risk-based test pyramid on PostgreSQL 18, protected squash-merge PRs, security/supply-chain gates, build once/sign, exact-digest staging-to-production promotion, and forward-safe migrations. | Traceable releases and strong rule coverage; more CI/runbook effort and manual production approval are required. | — |

No accepted ADR is currently superseded. A superseding ADR links the replaced record, changes its status to `Superseded`, and populates both records' index entries; accepted history is never deleted or silently rewritten.

## ADR format and lifecycle

Each ADR contains:

- title and sequential number;
- status (`Proposed`, `Accepted`, `Deprecated`, or `Superseded`), decision date, owners, and tracking links;
- context and forces that make a decision necessary;
- the selected choice at a durable architectural level;
- positive and negative consequences;
- rejected alternatives and why they do not fit the current scope; and
- compliance/revision rules, including the superseding decision when one exists.

A patch-level dependency or provider-specific implementation within an accepted boundary does not need a new ADR. A change to a major trust boundary, data authority, API compatibility rule, component/dependency direction, runtime/database line, deployment/recovery topology, or release-safety strategy does.

The consolidated entry point is [FBO Manager MVP architecture](../../architecture.md). Mapping from implementation tickets to decisions and constraints is in [Implementation traceability](../implementation-traceability.md).
