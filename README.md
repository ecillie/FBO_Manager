# FBO_Manager
FBO_Manager is a full-stack airport operations platform that helps FBOs manage aircraft arrivals, departures, service requests, customer details, and daily ramp activity through one centralized and user-friendly system.

## Delivery branches

- `FBODev` is the integration branch and the base for ordinary ticket pull requests.
- `Release-1.0.0` is the MVP release-candidate branch deployed to the non-production acceptance environment.
- `FBOProd` is the default, production-record branch; routine feature work does not merge directly into it.
- Short-lived ticket branches use `<issue-number>-<short-description>`, branch from `FBODev`, and are deleted after merge.

The release candidate is built once, verified in NonProd, merged to `FBOProd`, tagged `v1.0.0`, and deployed to production using the exact candidate artifact digests. See the [testing, CI/CD, and release strategy](docs/architecture/testing-and-release.md) for the complete branch, hotfix, and promotion policy.

## Project documentation

- [MVP delivery plan](docs/mvp-delivery-plan.md)
- [MVP architecture](docs/architecture.md)
- [MVP scope, actors, and workflows](docs/architecture/mvp-scope-and-workflows.md)
- [MVP quality attributes](docs/architecture/quality-attributes.md)
- [API contracts and operational data flows](docs/architecture/api-contracts-and-data-flows.md)
- [Security architecture and threat model](docs/architecture/security.md)
- [Environments and deployment topology](docs/architecture/deployment.md)
- [Observability, reliability, audit, and recovery](docs/architecture/operations-and-recovery.md)
- [Testing, CI/CD, and release strategy](docs/architecture/testing-and-release.md)
- [Architecture decision index](docs/architecture/decisions/README.md)
- [Architecture-to-implementation traceability](docs/architecture/implementation-traceability.md)
- [Frontend application architecture](docs/architecture/decisions/0001-frontend-application-architecture.md)
- [Backend application architecture](docs/architecture/decisions/0002-backend-application-architecture.md)
- [API contract architecture decision](docs/architecture/decisions/0003-api-contracts-and-operational-data-flows.md)
- [Security architecture decision](docs/architecture/decisions/0004-delegated-identity-and-capability-authorization.md)
- [Deployment architecture decision](docs/architecture/decisions/0005-portable-single-region-container-deployment.md)
- [Operations and recovery architecture decision](docs/architecture/decisions/0006-managed-telemetry-and-tested-backup-recovery.md)
- [Testing and release architecture decision](docs/architecture/decisions/0007-layered-verification-and-immutable-promotion.md)
- [Branch and environment promotion architecture decision](docs/architecture/decisions/0008-environment-aligned-branch-promotion.md)
- [Database design](docs/database-design.md)
- [Database ER diagram](docs/database-er-diagram.md)
- [Database schema baseline](db/README.md)
