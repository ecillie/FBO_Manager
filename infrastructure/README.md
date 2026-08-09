# FBO Manager infrastructure

Infrastructure implementation has not been created yet. The accepted MVP topology, environment boundaries, ports, configuration/secret handling, PostgreSQL connectivity, migration order, health behavior, and resource assumptions are defined in:

- [ADR 0005: Portable single-region container deployment](../docs/architecture/decisions/0005-portable-single-region-container-deployment.md);
- [Environments, configuration, and deployment topology](../docs/architecture/deployment.md);
- [ADR 0006: Managed telemetry and tested backup recovery](../docs/architecture/decisions/0006-managed-telemetry-and-tested-backup-recovery.md); and
- [Observability, reliability, audit, and recovery](../docs/architecture/operations-and-recovery.md).

The MVP will use provider-neutral OCI web/API images, a one-shot migration job, one managed PostgreSQL 18 primary, and managed DNS/TLS, secrets, telemetry, and backups. Provider-specific infrastructure code and operating commands must preserve those boundaries and must not contain environment credentials.

Multi-zone application/database availability, point-in-time recovery, a service mesh, and Kubernetes are not MVP requirements. They require Release One evidence and review.
