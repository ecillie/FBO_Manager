# FBO Manager backend

The MVP backend will be one synchronous Java 25 and Spring Boot 4.1 application backed by PostgreSQL. It is designed as a capability-oriented modular monolith: one deployable application and one transaction boundary, with internal module rules enforced by Spring Modulith and ArchUnit.

The accepted design is recorded in [ADR 0002: Backend application architecture and dependency boundaries](../docs/architecture/decisions/0002-backend-application-architecture.md). The [API contracts and operational data flows](../docs/architecture/api-contracts-and-data-flows.md), [security architecture](../docs/architecture/security.md), [deployment architecture](../docs/architecture/deployment.md), [operations and recovery model](../docs/architecture/operations-and-recovery.md), [testing and release strategy](../docs/architecture/testing-and-release.md), [ADR index](../docs/architecture/decisions/README.md), [MVP architecture](../docs/architecture.md), [workflow definitions](../docs/architecture/mvp-scope-and-workflows.md), [quality attributes](../docs/architecture/quality-attributes.md), [database design](../docs/database-design.md), and [initial PostgreSQL schema](../db/init/001_schema.sql) are the other controlling sources.

## Selected foundation

- Java 25 LTS and Spring Boot 4.1.0
- Spring MVC with embedded Tomcat
- Apache Maven 3.9.16 through the Maven Wrapper
- Spring Modulith 2.1.0 for closed capability modules and dependency verification
- Spring Data JPA/Hibernate for transactional persistence adapters
- Spring `JdbcClient` for operational projections and database views
- PostgreSQL JDBC with HikariCP
- Flyway handwritten SQL migrations
- Jakarta Bean Validation and centralized Spring MVC error mapping
- Spring Boot Actuator liveness and database-aware readiness
- JUnit, Spring Boot Test, Testcontainers PostgreSQL, and ArchUnit

Spring Boot's dependency management owns compatible framework, driver, and test-library versions unless an override has a documented compatibility reason. Hibernate validates the Flyway-managed schema; it does not create or update it. Repository and concurrency tests run against PostgreSQL rather than H2.

## Intended source shape

```text
backend/
  pom.xml
  .mvn/
  mvnw
  mvnw.cmd
  src/main/java/com/ecillie/fbomanager/
    FboManagerApplication.java
    administration/
    aircraft/
    parking/
    visits/
    services/
    fleet/
    fuel/
    workforce/
    tasks/
    operations/
    platform/
  src/main/resources/
    application.yml
    db/migration/
  src/test/java/com/ecillie/fbomanager/
```

Each business module exposes a narrow named API and keeps its implementation internal:

```text
visits/
  package-info.java
  api/
  internal/
    web/
    application/
    domain/
    persistence/
```

The dependency direction is HTTP controllers to application services to domain rules and repository ports. Persistence adapters implement repository ports and map JPA/JDBC records into domain or application types. Controllers never access repositories directly, and JPA entities never become API responses or cross-module contracts.

Application-service command methods own Spring transactions. Services select deterministic row locks for parking, visit, shift, task-dispatch, and fuel workflows; repositories provide locking primitives but never commit or roll back. The PostgreSQL constraints, triggers, and partial indexes remain the final integrity backstop.

## Implementation ownership

This directory is intentionally documentation-only until the Backend MVP implementation issues create the application:

- issue #10 scaffolds the Spring Boot application and developer environment;
- issue #11 establishes Flyway migrations and PostgreSQL data access;
- issue #8 implements domain mappings and repository adapters;
- issue #7 implements transactional services and workflows;
- issue #12 implements shared HTTP conventions and error handling; and
- issue #27 turns this file into the complete setup, configuration, and operations guide.

Until those issues are implemented, do not infer runnable commands or environment variables beyond the accepted architecture documents.
