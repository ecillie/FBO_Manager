# MVP Scope, Actors, and Workflows

## 1. Purpose

This document defines who uses FBO Manager, which operational outcomes the MVP supports, and which invariants must survive both successful and failed workflows. It expands the architecture-driver summary in [the main architecture document](../architecture.md).

## 2. Actors

The names below describe operational responsibilities. The exact seeded roles and enforced capabilities are defined in the [security architecture](security.md#4-roles-and-capabilities); a configured role may combine these responsibilities.

| Actor | Primary responsibilities | Typical write authority |
| --- | --- | --- |
| Administrator | Configure the airport, reference data, users, roles, parking, fleet, and fuel holders. | Configuration and administrative records. |
| Manager | Supervise the operation, resolve exceptions, approve overrides, and review audit/history data. | Most operational records and controlled overrides. |
| CSR/dispatcher | Schedule aircraft, update visit status, assign parking, capture service requests, and dispatch work. | Visits, parking assignments, services, and tasks. |
| Line-service worker | View assigned work, start/complete tasks, and report operational notes or exceptions. | Own eligible task transitions and permitted service updates. |
| Fueler | Perform fuel transfers and dispenses and record the associated inventory evidence. | Authorized fuel tasks and append-only inventory transactions. |
| Read-only user | Monitor the ramp board, queues, fleet, staffing, and inventory without changing state. | None. |

An individual worker may hold a configured role that combines responsibilities. Authorization is based on explicit capabilities, not on trusting a job-title string in a client request.

## 3. Scope boundary

### 3.1 Included capabilities

- Configure one airport and its timezone.
- Maintain customers, aircraft reference data, operational classifications, and physical aircraft.
- Schedule and operate an aircraft visit from expected arrival through departure or cancellation.
- Configure nested parking areas and spots and assign an on-ramp aircraft to one available spot.
- Capture and manage multiple service requests for a visit.
- Maintain service vehicles and the fuel-truck subtype.
- Maintain fuel tanks and record auditable inventory movements for tanks and trucks.
- Maintain workers and shifts and derive who is currently at work.
- Create and dispatch airport-wide or aircraft-related tasks.
- Derive ramp, parking, service, worker, vehicle, and fuel current-state views.

### 3.2 Explicitly excluded capabilities

- More than one airport in a deployment.
- Customer billing, invoices, card processing, or accounting exports.
- Flight-plan, flight-tracking, weather, NOTAM, or fuel-vendor integrations.
- Customer self-service portals.
- Native mobile clients and offline operation during the MVP. A future native client will reuse the backend API and requires a separate architecture decision.
- Analytics, forecasting, and data-warehouse pipelines.
- Automated parking or dispatch decisions that cannot be reviewed or overridden by an authorized user.
- Release One production-resilience features unless separately prioritized.

## 4. Critical workflow: aircraft turnaround

### 4.1 Normal path

1. A dispatcher identifies or creates the customer, aircraft model, and physical aircraft, assigning the aircraft a configured operational classification.
2. The dispatcher schedules an `EXPECTED` visit with estimated arrival and departure information.
3. The dispatcher adds zero or more service requests.
4. The visit becomes `INBOUND` when the aircraft is approaching.
5. The system recommends compatible available parking using category preferences; the dispatcher selects or overrides the recommendation.
6. Arrival records the actual arrival time and moves the visit to `ON_RAMP` in one transaction with the parking assignment.
7. Workers complete requested services through associated tasks.
8. Departure records the actual departure time and moves the visit to `DEPARTED`, releasing the spot.

### 4.2 Required invariants

- An aircraft has no more than one active visit.
- A spot has no more than one `ON_RAMP` visit.
- An out-of-service or occupied spot cannot be assigned.
- Estimated and actual departure times do not precede their corresponding arrival times.
- An `ON_RAMP` visit has an actual arrival time and parking spot.
- A `DEPARTED` visit has actual arrival and departure times.

### 4.3 Failure behavior

- A competing assignment receives a conflict response and no partial visit update.
- An invalid lifecycle transition is rejected without rewriting timestamps.
- A cancelled visit retains its history and releases no resource it did not hold.
- A manual parking override is permitted only to an authorized actor and remains visible in audit context.

## 5. Critical workflow: fuel fulfillment

### 5.1 Normal path

1. A dispatcher creates a fuel service request with fuel type, positive quantity, and unit.
2. A task is created for the same aircraft visit and request.
3. An eligible, on-shift worker and compatible available fuel truck are assigned.
4. If the truck's estimated inventory indicates that replenishment is needed, a fueler records a tank-to-truck transfer.
5. The system writes the negative tank entry and positive truck entry atomically with one transfer-group identifier.
6. The worker starts the task and service request.
7. Dispensing writes an append-only negative truck entry linked to the fuel service request and acting worker.
8. The service request and task complete with consistent timestamps.

### 5.2 Required invariants

- The service request, aircraft requirement, truck, tank, and ledger entries use compatible fuel types and units.
- Inventory balances are estimates and may fall below zero or exceed nominal capacity; out-of-range values remain visible for reconciliation.
- A transfer writes both sides or neither side.
- Ledger entries cannot be updated or deleted.
- A retry cannot create a duplicate financial or inventory effect.
- The worker and vehicle have no conflicting in-progress task.

### 5.3 Failure behavior

- An out-of-range estimated balance does not reject an otherwise valid movement.
- A fuel-type or unit mismatch rejects the complete operation.
- A failed paired transfer leaves both balances unchanged.
- Corrections use a compensating adjustment with a reason and actor.
- A network retry returns the original idempotent result or a clear conflict rather than dispensing twice.

## 6. Critical workflow: workforce dispatch

### 6.1 Normal path

1. A manager schedules a worker shift.
2. Clock-in records the actual start and makes the worker available for eligible work.
3. A dispatcher creates or selects a pending task.
4. The dispatcher assigns an eligible worker and optional compatible vehicle.
5. Starting the task atomically claims those resources.
6. Completing the task records the completion time and releases the resources.
7. Clock-out completes the shift after active work is resolved or reassigned.

### 6.2 Required invariants

- A worker has no more than one in-progress shift.
- A worker and vehicle each have no more than one in-progress task.
- Inactive workers and vehicles cannot receive new assignments.
- A vehicle marked out of service cannot start a task.
- A service-backed task refers to the same visit as its service request.

### 6.3 Failure behavior

- Concurrent dispatchers cannot claim the same worker or vehicle.
- A failed start leaves the task pending and resources unclaimed.
- An off-shift assignment requires an explicit authorized override; silent bypass is not allowed.

## 7. Critical workflow: airport administration

### 7.1 Normal path

1. An administrator configures the singleton airport and IANA timezone.
2. The administrator configures reference catalogs, aircraft models, parking hierarchy, fleet, tanks, workers, and roles.
3. Records that are no longer selectable are deactivated.
4. Operational history continues to resolve its original references.

### 7.2 Required invariants

- A deployment contains at most one airport record.
- Natural codes are normalized and treated as immutable during ordinary edits.
- Parking-area parents do not create a cycle.
- A fuel-truck subtype belongs only to a fuel-truck vehicle type.
- Used reference or operational records are not hard-deleted.

### 7.3 Failure behavior

- Unsafe deletion or incompatible reclassification is rejected.
- An occupied parking spot cannot be taken out of service.
- A natural-key correction, if supported, uses a controlled transaction and an audit record.

## 8. Sensitive operations and failure consequences

| Operation | Primary risk | Required architectural response |
| --- | --- | --- |
| Parking assignment | Two aircraft directed to one spot. | Transaction, uniqueness constraint, clear conflict, authorized override audit. |
| Worker/vehicle dispatch | One resource sent to competing tasks. | Transaction, row lock/partial uniqueness, no partial task transition. |
| Fuel receipt or transfer | Incorrect physical inventory estimate and financial exposure. | Append-only paired ledger, visible reconciliation state, actor, and idempotency key. |
| Fuel dispense | Duplicate or wrong-fuel service. | Compatibility checks, service link, idempotency, immutable evidence. |
| Inventory adjustment | Concealed loss or accidental balance corruption. | Restricted capability, mandatory reason, compensating entry, audit event. |
| Visit departure/cancellation | Lost service history or false occupancy. | Explicit state machine, timestamp checks, retained history. |
| Worker/role administration | Unauthorized operational access. | Least privilege, active-worker check, security audit. |

## 9. Planning assumptions

- The system may be used at any hour; no workflow assumes a nightly application shutdown.
- The shared MVP environment targets 25 concurrent staff users and 100 aircraft visits per operating day.
- PostgreSQL remains available as the authoritative transactional store; the MVP does not provide offline writes.
- The visible operations dashboard polls every 10 seconds under the decision in [ADR 0003](decisions/0003-api-contracts-and-operational-data-flows.md); push transport is deferred beyond the MVP.
- The airport timezone is configured before operational scheduling begins.
- Quantities use controlled units and fixed-precision decimals; automatic cross-unit conversion is not assumed.
- Historical operational records are retained. A legal retention and archival schedule is a later governance decision.

## 10. Accepted and deferred decisions

| Decision | Owner | Current baseline |
| --- | --- | --- |
| Identity and application sessions | [ADR 0004](decisions/0004-delegated-identity-and-capability-authorization.md) | Delegated OIDC login, exact active-worker link, and backend-owned opaque session. |
| Frontend framework and state libraries | [ADR 0001](decisions/0001-frontend-application-architecture.md) | React/TypeScript feature architecture with TanStack Query server state and explicit UI/form/URL ownership. |
| Backend framework and repository tooling | [ADR 0002](decisions/0002-backend-application-architecture.md) | Java/Spring modular monolith with application-service transactions and persistence ports/adapters. |
| Push updates beyond the polling baseline | Future architecture decision | [ADR 0003](decisions/0003-api-contracts-and-operational-data-flows.md) selects 10-second visible-page polling for the MVP. |
| Hosting topology | [ADR 0005](decisions/0005-portable-single-region-container-deployment.md) | Same-origin managed containers and one managed PostgreSQL 18 primary; exact conforming provider is launch configuration. |
| Telemetry and recovery | [ADR 0006](decisions/0006-managed-telemetry-and-tested-backup-recovery.md) | Managed redacted telemetry, daily encrypted backups, quarterly restore, 24-hour RPO/four-hour RTO. |

Provider selection and future push transport do not reopen the accepted MVP scope or application boundaries.
