# FBO Manager MVP Architecture

## 1. Purpose and status

This document is the entry point for the FBO Manager MVP architecture. It records the constraints and priorities that guide the more detailed frontend, backend, API, security, deployment, and testing decisions.

The current baseline covers the architecture drivers from GitHub issue [#28](https://github.com/ecillie/FBO_Manager/issues/28). Component diagrams, technology selections, and detailed design decisions will be added by the remaining architecture issues under the [MVP architecture epic](https://github.com/ecillie/FBO_Manager/issues/5).

The architecture is intentionally optimized for a single FBO operating at one airport. It is not a premature multi-tenant platform design.

## 2. Architecture drivers

The following principles have priority when design choices conflict:

1. **Operational correctness over write availability.** The system must reject a conflicting assignment or inventory movement instead of accepting inconsistent state.
2. **PostgreSQL is the source of truth.** Current operational state is derived from authoritative records, constraints, and transactions rather than duplicated flags.
3. **Critical writes are transactional.** Parking assignments, visit transitions, worker and vehicle dispatch, and fuel movements must complete atomically.
4. **History is retained.** Completed visits, services, tasks, shifts, and fuel transactions are not destroyed by normal application workflows.
5. **Fuel inventory is auditable.** Inventory uses an append-only ledger; corrections use compensating entries.
6. **Time has one storage convention.** Operational timestamps are stored in UTC and interpreted or displayed in the configured airport timezone.
7. **The MVP must remain simple to operate.** One application deployment and one PostgreSQL database are preferred until measured demand justifies additional distributed components.
8. **Security is part of the architecture.** Authentication, least-privilege authorization, encrypted transport, secret management, and audit context are required rather than deferred polish.

## 3. MVP scope

The MVP manages:

- one airport configuration and timezone;
- customers, aircraft manufacturers, models, physical aircraft, owners, and operators;
- expected, inbound, on-ramp, departed, and cancelled aircraft visits;
- nested parking areas, parking spots, availability, occupancy, and aircraft-category preferences;
- aircraft service requests, including fuel-specific quantities and units;
- service vehicles and fuel trucks;
- fuel tanks and append-only tank/truck inventory transactions;
- workers, roles, schedules, attendance, and derived at-work status;
- airport and aircraft-related tasks with worker and vehicle assignments; and
- current-state operational views used by the ramp board.

Detailed actors, workflow boundaries, and failure expectations are maintained in [MVP scope, actors, and workflows](architecture/mvp-scope-and-workflows.md).

## 4. Logical system boundary

The MVP has four logical responsibilities. Issue #29 will turn these into reviewed system-context and container diagrams.

| Responsibility | Purpose | Source of truth |
| --- | --- | --- |
| User application | Present operational workflows and collect validated user intent. | Backend API responses; no independent operational truth. |
| Backend API | Authenticate requests, enforce authorization, orchestrate business workflows, and expose current-state queries. | Application services plus PostgreSQL transactions. |
| PostgreSQL database | Persist operational history and enforce relational, uniqueness, capacity, and concurrency invariants. | Primary operational system of record. |
| Identity boundary | Establish worker identity and session validity. | Exact local or delegated approach is decided by issue #33. |

The MVP does not require a message broker, microservice network, separate analytics store, or distributed cache. A later architecture decision may add a component only when a documented workflow or measured quality target requires it.

## 5. Critical workflows

Architecture and testing must cover these end-to-end paths:

1. **Aircraft turnaround:** schedule a visit, mark the aircraft inbound, assign an available spot, record arrival, perform requested services, and record departure.
2. **Fuel fulfillment:** create a fuel request, dispatch an eligible worker and compatible truck, transfer inventory when necessary, dispense fuel, and retain a complete audit trail.
3. **Workforce dispatch:** schedule and start a shift, assign a worker and optional vehicle to a task, start and complete the task, and derive current availability.
4. **Airport administration:** configure the singleton airport, parking layout, reference catalogs, fleet, fuel tanks, and workers without damaging operational history.

Detailed steps, invariants, and expected failure behavior are in [MVP scope, actors, and workflows](architecture/mvp-scope-and-workflows.md).

## 6. Data and consistency baseline

The [database design](database-design.md), [ER diagram](database-er-diagram.md), and initial [PostgreSQL schema](../db/init/001_schema.sql) define the current data model. Architecture decisions must preserve these core invariants:

- no more than one active visit per aircraft;
- no more than one on-ramp aircraft per parking spot;
- no cyclic parking-area hierarchy;
- fuel-service requests contain a compatible fuel type, positive quantity, and unit;
- a worker and vehicle each have no more than one in-progress task;
- a worker has no more than one in-progress shift;
- fuel balances never fall below zero or exceed holder capacity;
- each fuel transaction affects exactly one tank or truck;
- paired transfers commit both ledger entries or neither; and
- current worker and vehicle status is derived rather than independently edited.

## 7. Planning scale

The following values are design and test baselines, not commercial limits:

| Dimension | Expected MVP load | Verification headroom |
| --- | ---: | ---: |
| Concurrent authenticated staff | 25 | 50 |
| Aircraft visits per operating day | 100 | 500 |
| Service requests per operating day | 500 | 2,500 |
| Tasks per operating day | 1,000 | 5,000 |
| Configured parking spots | 100 | 500 |
| Active workers | 100 | 500 |
| Active service vehicles | 100 | 500 |

The values must be revisited with pilot-airport data. The design should prefer correct indexed PostgreSQL queries and bounded API results rather than introducing distributed infrastructure for hypothetical scale.

## 8. Quality-attribute baseline

The detailed scenarios and verification methods are defined in [MVP quality attributes](architecture/quality-attributes.md). The headline targets are:

- normal API reads have a 95th-percentile response time below 500 ms;
- normal operational writes have a 95th-percentile response time below 1 second;
- concurrency-sensitive conflicts fail clearly without partial state;
- the shared MVP environment targets 99.5% monthly availability, excluding planned maintenance;
- automated daily backups support a 24-hour recovery-point objective and four-hour recovery-time objective;
- all access is authenticated except explicit health endpoints;
- authorization is capability-based and tied to active workers;
- sensitive operational changes record actor, time, target, and outcome; and
- logs and API errors never expose secrets, credentials, raw SQL, or stack traces to clients.

## 9. Constraints and non-goals

The following are outside the MVP architecture:

- multi-airport tenancy or cross-airport data sharing;
- billing, invoicing, payments, or accounting integrations;
- external flight tracking, weather, airport, or fuel-vendor integrations;
- offline-first operation or a native mobile application;
- analytics and data-warehouse workloads;
- microservices introduced only for organizational scale;
- production multi-zone high availability and point-in-time recovery, which remain Release One goals; and
- automatic operational decisions that remove the dispatcher’s ability to make an authorized override.

## 10. Decision ownership and follow-up

| Decision area | Tracking issue | Expected artifact |
| --- | --- | --- |
| Context and containers | #29 | System-context and container diagrams |
| Frontend structure | #30 | Frontend architecture decision record |
| Backend boundaries | #31 | Backend architecture decision record |
| API and data flows | #32 | API conventions and sequence diagrams |
| Security | #33 | Identity decision, capability matrix, and threat model |
| Deployment | #34 | Environment and deployment topology |
| Operations | #35 | Observability and recovery model |
| Testing and release | #36 | Test and delivery strategy |
| Consolidation | #37 | Reviewed architecture document and ADR index |

Architecture documentation changes with implementation changes. A pull request that changes a major boundary, trust relationship, persistence rule, or deployment assumption must update the relevant architecture document or decision record.
