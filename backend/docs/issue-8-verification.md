# Issue 8 repository verification

Issue [#8](https://github.com/ecillie/FBO_Manager/issues/8) is implemented by framework-neutral models and repository ports in each module's `api` package and PostgreSQL `JdbcClient` adapters in each module's `internal.persistence` package.

| Acceptance area | Implementation and verification |
| --- | --- |
| Complete model | Administration, aircraft, parking, visits, services, fleet, fuel, workforce, and task model groups map every table in `V1__baseline_schema.sql`. |
| Lossless values | PostgreSQL enums map to closed Java enums, `NUMERIC(14,3)` maps through `FixedPrecisionQuantity`, `TIMESTAMPTZ` maps through UTC `Instant`, generated IDs remain `long`, and optional columns remain nullable. |
| Composite and immutable keys | `AircraftModelKey` maps the manufacturer/model composite key. `NaturalKey` centralizes normalization; repository upserts never replace a natural primary key. |
| Bounded queries | `RepositoryPageRequest` enforces a maximum of 100 and every list method has explicit filters, an allowlisted sort, and a stable identifier tie-breaker. |
| Transactions and locks | Repository writes join the caller transaction. Parking, visit, service, vehicle, worker, task, tank, and truck lock methods require an active transaction and use `FOR UPDATE`. |
| Derived state | Tank balance, truck balance, service-vehicle status, and worker status views have read-only mappings. |
| Relationship loading | Visit detail and ramp-board reads join aircraft/model/parking in one query. Operational visit detail joins and deduplicates services and tasks from one query. |
| PostgreSQL behavior | `PostgreSqlDataAccessIntegrationTests` covers every repository, normalized and composite keys, microsecond timestamps, exact decimals, all four views, locks, constraint translation, append-only entries, and rollback. |

Run the complete verification from `backend/`:

```shell
./mvnw test
```
