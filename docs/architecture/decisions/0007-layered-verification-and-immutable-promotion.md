# ADR 0007: Layered verification and immutable artifact promotion

- **Status:** Accepted
- **Date:** 2026-08-09
- **Decision owners:** FBO Manager architecture contributors
- **Tracking issue:** [#36](https://github.com/ecillie/FBO_Manager/issues/36)
- **Implementation issues:** [#25](https://github.com/ecillie/FBO_Manager/issues/25) and [#26](https://github.com/ecillie/FBO_Manager/issues/26)

> **Amendment:** [ADR 0008](0008-environment-aligned-branch-promotion.md) replaces this record's `main`-branch assumptions, independent-approval requirement for the current solo-developer phase, artifact source, and named promotion branches. The verification, supply-chain, migration, build-once, and exact-digest requirements below remain accepted.

## Context

FBO Manager's hardest rules depend on PostgreSQL transactions, partial unique indexes, locks, triggers, views, append-only history, exact HTTP representation, and capability boundaries. Pure unit tests or a substitute database cannot prove those behaviors. Conversely, relying mainly on slow end-to-end tests would make failures difficult to localize and delivery unreliable.

Releases combine static frontend assets, a modular backend, ordered schema migrations, environment configuration, and an external identity provider. Rebuilding per environment or automatically reversing database migrations could deploy different inputs or destroy committed data. The small MVP team needs strong repeatability without operating a sophisticated progressive-delivery platform.

## Decision

Adopt a risk-based test pyramid: backend/frontend unit and component tests; automated module/architecture checks; PostgreSQL 18 repository/migration tests; full API/security/OpenAPI tests; synchronized real-database concurrency tests; a narrow Playwright critical-path suite; and scheduled/pre-release performance, failure, and restore exercises.

Use deterministic builders, fixed clocks/seeds, disposable databases, and real Flyway migrations. Enforce coverage backstops of 90% line/85% branch for backend domain/application code, 80%/75% for behavior-bearing backend/frontend totals, plus enumerated coverage of state transitions, capability mappings, audit families, and critical race cases. Do not rerun failed assertions to hide flakiness.

Protect the integration, active release, and production-record branches with pull requests, current required checks, resolved review, and the solo-review/approval transition defined by ADR 0008. Ticket pull requests squash into `FBODev`; release and production promotions follow the versioned branch policy. CI enforces format/lint/type/static/architecture tests, PostgreSQL/API/OpenAPI/client consistency, dependency lockfiles, secret/vulnerability/license/image controls, least-privilege workflows, and build reproducibility.

Build the API image, web image, OpenAPI/client artifacts, SBOMs, provenance, and release manifest once from the accepted `Release-<version>` candidate commit. Sign them with short-lived CI identity and promote exact digests to NonProd, then manually approved production after the release tree merges to `FBOProd`. Apply migrations once with the API digest's migration command before replacement. Use expand/migrate/contract; roll back only to a schema-compatible application and otherwise forward-fix. Verify health, auth, dashboard/critical workflows, telemetry, and audit during a minimum 30-minute ordinary-release observation window.

The normative test map, fixtures/isolation, migration checks, branch controls, supply-chain controls, promotion sequence, architecture-update triggers, and intentional compromises are in [Testing, CI/CD, and release strategy](../testing-and-release.md).

## Consequences

### Benefits

- Each business risk is proven at the lowest layer capable of observing it, with PostgreSQL-specific behavior tested on PostgreSQL 18.
- Architecture and OpenAPI drift fail before merge.
- Deterministic isolation and a no-hidden-rerun policy make failures trustworthy.
- Build-once promotion makes NonProd evidence relevant to the exact production bytes.
- SBOM, provenance, scanning, review, migration, backup, and observation evidence make releases traceable.

### Costs and risks

- PostgreSQL, concurrency, browser, performance, and restore tests require more CI time and maintenance than unit-only testing.
- Coverage and enumerated-rule gates require careful exclusions and fixture design to avoid metric gaming.
- Manual production approval and forward-fix decisions require an available responsible owner.
- A shared NonProd environment can queue unrelated acceptance work and is less isolated than per-PR previews.
- Security tools can produce false positives; exceptions need ownership, expiry, and compensating controls.

## Rejected alternatives

| Alternative | Reason not selected for the MVP |
| --- | --- |
| Unit tests with mocked repositories only | They cannot prove constraints, transactions, locks, triggers, views, migrations, serialization, or real SQL behavior. |
| H2/SQLite as integration database | Their types, indexes, locking, constraints, and SQL behavior differ from the PostgreSQL source of truth. |
| End-to-end-heavy testing | It is slower, less deterministic, and poor at isolating domain/database/contract failures. |
| Coverage percentage as the sole quality gate | Executed lines do not prove state edges, security boundaries, rollback, idempotency, or concurrency outcomes. |
| Automatic rerun of failed tests | It hides flakiness and can approve nondeterministic correctness. Only pre-test infrastructure setup may retry. |
| Direct or force pushes to `FBODev`, an active release branch, or `FBOProd` | They weaken required-check/review enforcement and make changes harder to trace. Ticket squash merges and explicit release promotion fit the project. |
| Rebuild separately in NonProd and production | Dependency/base-image drift could make production different from the tested candidate. |
| Automatic down migrations on rollback | Reversing a schema/data migration can lose committed operational history. Backward-compatible app rollback or forward fix is safer. |
| Full canary/blue-green/preview platform now | One-instance MVP topology and user scale do not justify the additional routing, data, and environment complexity. |

## Compliance and revision

Issue #25 implements backend test layers and critical cases. Issue #26 implements protected checks, scans, artifact creation, and documents branch/environment settings. Frontend implementation issues must adopt the component and browser layers before their workflows are considered releasable.

Changing PostgreSQL parity, required concurrency/security coverage, branch/review protections, build-once promotion, migration safety, or production approval requires architecture/release review and an ADR update or superseding decision when it changes the strategy rather than a tool detail. ADR 0008 controls the current branch names, solo-review phase, and promotion path.
