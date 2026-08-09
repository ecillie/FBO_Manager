# ADR 0005: Portable single-region container deployment

- **Status:** Accepted
- **Date:** 2026-08-09
- **Decision owners:** FBO Manager architecture contributors
- **Tracking issue:** [#34](https://github.com/ecillie/FBO_Manager/issues/34)
- **Implementation issues:** [#10](https://github.com/ecillie/FBO_Manager/issues/10), [#11](https://github.com/ecillie/FBO_Manager/issues/11), and [#26](https://github.com/ecillie/FBO_Manager/issues/26)

## Context

The MVP needs reproducible local and CI environments plus an operable shared staging and production/pilot topology. It serves one airport and roughly 25 concurrent staff, so multi-cluster orchestration would create more operational burden than availability benefit. PostgreSQL is authoritative and needs stronger isolation, backup, and lifecycle controls than an application container filesystem.

Security requires a same-origin browser experience, private database and management paths, runtime secret injection, and distinct environment identities. Releases need immutable artifacts and one controlled migration owner. The architecture must preserve a path to Release One high availability without claiming that the MVP meets multi-zone or point-in-time-recovery goals.

## Decision

Use provider-neutral OCI images on a single-region managed container service for shared environments. Deploy an immutable static `fbo-web` image and a Java 25/Spring Boot 4.1 `fbo-api` image at one public HTTPS origin. Run Flyway as a short-lived `fbo-migrate` command from the exact API image digest before replacing the API. Use a separately managed PostgreSQL 18 primary reachable only over verified TLS on a private network.

Use Node.js 24 LTS for frontend builds, Java 25 LTS for backend build/runtime, and PostgreSQL 18 in local, CI, staging, and production. Exact dependencies, patch images, package manager, and image digests are repository-pinned. Local development uses containers and CI creates an ephemeral PostgreSQL 18 service.

Separate local, CI, staging, and production configuration, OIDC clients, credentials, databases, telemetry, and backups. Store non-secret settings in reviewed deployment configuration and inject secrets from a platform secret manager through mounted files/workload identity. Separate migration, application, backup, and break-glass database responsibilities.

Expose only HTTPS `443` publicly; redirect `80`. Keep API, management, and PostgreSQL ports private. Start one API instance with a 10-connection Hikari pool, explicit limits and probes, and graceful shutdown. Promise low rather than zero downtime. Use expand/migrate/contract changes, no automatic production down migrations, application rollback only with backward-compatible schema, and a new forward migration otherwise.

The normative topology, ports, resource baseline, configuration classes, migration order, probes, and ownership are in [Environments, configuration, and deployment topology](../deployment.md).

## Consequences

### Benefits

- Local, CI, and shared environments use the same runtime/database major lines and immutable release inputs.
- A managed container platform and managed PostgreSQL remove host and database backup mechanics without imposing Kubernetes.
- Same-origin routing simplifies secure cookies, CORS, and CSRF while preserving separate frontend/backend artifacts.
- A migration job with separate credentials makes schema ownership and release order explicit.
- Provider-neutral OCI, PostgreSQL, OIDC, JSON/HTTPS, and OTLP boundaries retain deployment portability.

### Costs and risks

- One web/API instance and one database primary are MVP failure domains; brief replacement downtime or a maintenance window is accepted.
- Managed-platform capabilities and exact secret/network syntax still require a provider-specific operating guide.
- A separate web image and backend image require compatible promotion and smoke verification.
- Forward-only migrations demand expand/contract discipline and sometimes retain transitional schema across releases.
- Provider-neutral architecture does not make live migration between vendors automatic; configuration, DNS, backup, and access work still exists.

## Rejected alternatives

| Alternative | Reason not selected for the MVP |
| --- | --- |
| Kubernetes or a service mesh | The single-airport scale and one modular backend do not justify cluster, ingress, policy, upgrade, and observability complexity. |
| One VM running application and PostgreSQL | It couples application and authoritative data failure, patching, capacity, backup, and restore responsibilities on one host. Managed PostgreSQL creates a cleaner data boundary. |
| Serverless functions for each module | Critical workflows need explicit transactions, shared pooling, predictable cold-path behavior, and modular-monolith boundaries; function decomposition adds distributed coordination. |
| Platform-specific managed application framework | Deep coupling is unnecessary. OCI images and standard health/network/config contracts meet the MVP need. |
| Database migration at every API startup | Concurrent startup can race, and the running application would require schema-owner credentials. A release job provides one accountable migration step. |
| Separate public frontend and API origins | It adds CORS, cookie, and CSRF complexity without an MVP delivery benefit. |
| PostgreSQL in the application container or persistent container volume | It weakens failure isolation, backup/restore responsibility, upgrades, and data durability. |
| Multi-zone HA and PITR immediately | They are Release One goals. The MVP accepts a four-hour RTO and 24-hour RPO with daily backups and restore tests. |

## Compliance and revision

Issue #10 implements the runnable foundation and local environment. Issue #11 implements migration/data-access rules. Issue #26 implements build, artifact, parity, and promotion gates. Issue #27 documents provider-specific commands and configuration without committing secrets.

Selecting a deployment provider within these contracts does not require a new ADR. Changing the public-origin model, deployable units, database engine/major, secret boundary, migration ownership, or MVP availability topology does require a superseding decision and coordinated security, operations, and release updates.
