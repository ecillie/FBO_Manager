# Issue 7 service and workflow verification

Issue [#7](https://github.com/ecillie/FBO_Manager/issues/7) is implemented by framework-neutral service contracts in each module's `api` package and transactional Spring services in each module's `internal.application` package.

| Acceptance area | Implementation and verification |
| --- | --- |
| HTTP boundary | Controllers call application services. Feature workflows expose service interfaces; repository ports remain behind the application boundary. |
| Capability coverage | Administration, aircraft, parking, visits, service requests, fleet, fuel, workforce, tasks, and the current-state operations dashboard have explicit service APIs. |
| State transitions | Visit, service-request, task, and shift commands validate their required source state and produce typed `*_STATE_CONFLICT` errors for invalid transitions. |
| Transactions and locks | Public commands own `@Transactional` boundaries. Arrival locks a spot before its visit, dispatch locks worker and vehicle before task, shift start locks worker and shift, and fuel commands lock holders before append-only writes. |
| Business rules | Active-visit/spot/resource uniqueness remains database-backed; services enforce active resources, time ordering, fuel request fields, holder compatibility, append-only adjustments, field-service task creation, and assignment eligibility. Advisory parking preferences and estimate-over-capacity behavior are intentionally not hard rejections. |
| Domain errors | The framework-neutral `DomainException` hierarchy distinguishes validation, not found, conflict, authorization, and unexpected failures. The global HTTP advice maps each category without exposing persistence details. |
| Idempotency | Mutation services use `JdbcIdempotentCommandExecutor`. A key is claimed before business effects, its canonical command plus trusted actor is SHA-256 fingerprinted, and the authoritative response is committed in the same transaction. Matching retries replay; mismatched reuse conflicts; rollback removes both claim and effect. Ordinary records live at least 24 hours, while inventory records link to immutable ledger evidence without expiry. |
| Time | `AirportTime` centralizes the UTC clock, configured airport `ZoneId`, local-to-UTC conversion, and operating-date interpretation. Persistence models continue to store authoritative `Instant` values. |
| Tests | `WorkflowApplicationServiceTests` uses controlled repository/resource doubles to verify state, orchestration, and lock order. `PostgreSqlDataAccessIntegrationTests` verifies service-level commit, replay, uniqueness conflict, and rollback of an idempotency claim against PostgreSQL 18. |

Run the complete verification from `backend/`:

```shell
./mvnw test
```
