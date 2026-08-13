# Issue 11 verification

Verification date: 2026-08-11

## Baseline-to-Flyway mapping

The former runtime/bootstrap file `db/init/001_schema.sql` was removed. Its original contents remain only in `src/test/resources/db/baseline/001_schema.sql` as a non-packaged upgrade fixture.

| Historical content/change | Authoritative migration |
| --- | --- |
| Original enums, `set_updated_at`, all operational/reference tables, named checks and foreign keys, partial indexes, sequences, business-rule functions/triggers, update-time triggers, and four current-state/balance views | `V1__baseline_schema.sql`; only the `psql`-specific `\\set`, `BEGIN`, and `COMMIT` wrapper was removed for Flyway transaction ownership. A test proves the normalized files match exactly. |
| API architecture's transactional idempotency record, composite uniqueness, retention constraints, and cleanup/ledger indexes | `V2__transactional_idempotency.sql` |
| Application-role DML/view/sequence/function grants and denials for schema creation, Flyway history, and fuel-ledger mutation | `V3__application_privileges.sql` |
| Generic fuel, aircraft category/operation, service, service-vehicle type, and worker-role records | `R__reference_data.sql` |

## PostgreSQL 18 acceptance evidence

Command run from `backend/`:

```bash
./mvnw --batch-mode --no-transfer-progress -Dtest=PostgreSqlDataAccessIntegrationTests test
```

Result: `BUILD SUCCESS`; 5 tests run, 0 failures, 0 errors, 0 skipped against `postgres:18.3-alpine` at the repository-pinned digest.

The suite proves:

- an empty PostgreSQL 18 database applies V1, V2, V3, and the repeatable seed and passes Flyway checksum validation;
- the committed historical baseline fixture is baselined at version 1 and upgrades through V2, V3, and the repeatable seed;
- all six PostgreSQL enums, four derived views, representative business functions/triggers, named check constraints, and partial-index predicates are present;
- an unchanged direct rerun of `R__reference_data.sql` produces an identical all-column JSON snapshot, including unchanged timestamps;
- the application role can use application tables/sequences but cannot create schema objects, read Flyway history, or update the append-only fuel ledger;
- the Spring-managed Hikari data source has the accepted 10/2/30-second pool bounds, backs the JPA transaction manager, and shares rollback behavior across `JdbcClient` and JPA;
- Hibernate starts with mapping validation and PostgreSQL 18 metadata rather than schema generation or an H2/SQLite substitute.

The complete CI-ready command is:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

Result: `BUILD SUCCESS`; 19 tests run, 0 failures, 0 errors, 0 skipped.

The backend workflow runs that command, applies the packaged one-shot `migrate` profile to its smoke database, and only then starts the ordinary API process for liveness/readiness checks.
