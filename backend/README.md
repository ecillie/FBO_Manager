# FBO Manager backend

The backend is one synchronous Spring MVC application under `com.ecillie.fbomanager`. It is a capability-oriented modular monolith: one deployable JAR with closed module boundaries enforced by Spring Modulith.

The controlling design is [ADR 0002: Backend application architecture and dependency boundaries](../docs/architecture/decisions/0002-backend-application-architecture.md). See also the [API contracts and data flows](../docs/architecture/api-contracts-and-data-flows.md), [security architecture](../docs/architecture/security.md), [deployment architecture](../docs/architecture/deployment.md), [operations and recovery model](../docs/architecture/operations-and-recovery.md), and [testing and release strategy](../docs/architecture/testing-and-release.md).

## Pinned foundation

| Component | Version/source |
| --- | --- |
| Java | Temurin/OpenJDK 25 LTS |
| Spring Boot | `4.1.0`, through the Spring Boot parent |
| Spring Modulith | `2.1.0`, through its BOM |
| Maven | `3.9.16`, through the committed Maven Wrapper |
| PostgreSQL | `18.3-alpine`, pinned by multi-architecture image digest |
| Spotless | `3.9.0`, with Eclipse formatter `4.40` |

Use `./mvnw` for every Maven command. A system Maven installation is neither required nor used by CI.

The persistence foundation uses the PostgreSQL JDBC driver, HikariCP, Spring Data JPA/Hibernate, Spring JDBC `JdbcClient`, and Flyway. JPA mappings are validated against the migrated schema and never generate DDL. Application workflows and persistence adapters are added by their follow-up tickets.

## Prerequisites

- JDK 25
- Docker Desktop or Docker Engine with the Compose plugin
- `curl` for manual health checks

On macOS with Homebrew:

```bash
brew install --cask temurin@25
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
java --version
```

The first wrapper command downloads Maven 3.9.16 into the user Maven cache. Confirm both pins from `backend/`:

```bash
./mvnw --version
```

## Start locally

From `backend/`:

```bash
cp .env.example .env
```

Replace `replace-with-local-password` in `.env` with a local-only password. Never commit `.env` or a real credential.

Start the pinned database and wait for its health check:

```bash
docker compose --env-file .env up --detach --wait postgres
```

Export the settings, run the one-shot migration profile, and then run the service:

```bash
set -a
source .env
set +a

SPRING_PROFILES_ACTIVE=migrate ./mvnw spring-boot:run
./mvnw spring-boot:run
```

The migration command validates checksums, applies pending versioned migrations plus the idempotent reference seed, and exits. The ordinary application has Flyway disabled, validates JPA mappings with `ddl-auto=validate`, and rejects missing or invalid configuration. Stop it with `Ctrl+C`; graceful shutdown refuses new traffic and allows active requests up to the configured timeout.

Stop PostgreSQL while preserving its local data:

```bash
docker compose --env-file .env down
```

To intentionally erase the local database and recreate it from scratch:

```bash
docker compose --env-file .env down --volumes
docker compose --env-file .env up --detach --wait postgres
SPRING_PROFILES_ACTIVE=migrate ./mvnw spring-boot:run
```

`down --volumes` permanently deletes only this Compose project's named `postgres-data` volume. It does not target another PostgreSQL database or Docker volume. The following two commands recreate the project database and apply the authoritative Flyway path.

## Migrations and database identities

The only runtime schema source is [`src/main/resources/db/migration`](src/main/resources/db/migration):

| Migration | Responsibility |
| --- | --- |
| `V1__baseline_schema.sql` | Flyway-compatible conversion of the former `db/init/001_schema.sql`, preserving all PostgreSQL enums, tables, constraints, partial indexes, functions, triggers, sequences, and current-state views. |
| `V2__transactional_idempotency.sql` | Transactional command-result persistence, namespaced key uniqueness, retention constraints, and cleanup/ledger indexes. |
| `V3__application_privileges.sql` | Least-privilege grants to the externally created role named by `FBO_APPLICATION_DATABASE_ROLE`; denies schema creation, Flyway-history access, and fuel-ledger mutation. |
| `R__reference_data.sql` | Idempotent generic fuel, aircraft-category/operation, service, vehicle-type, and worker-role catalogs. Airport-specific data is never seeded. |

A released `V*` migration is immutable. Never repair a checksum by editing an applied file or manually patch a shared database. Use a new forward migration and expand/migrate/contract: add backward-compatible structures first, backfill in bounded work, switch application behavior, and remove obsolete structures only in a later release. There are no production down migrations. Roll back the application only when the migrated schema remains backward compatible; otherwise deploy a new forward fix. Database restore is a disaster-recovery decision, not an ordinary release rollback.

Shared environments create the migration and application login roles outside Flyway and inject separate credentials. The migration role owns/changes the schema and Flyway history and is used only with `SPRING_PROFILES_ACTIVE=migrate`. The application role runs the API and cannot alter schema or migration history. Run the migration profile from the exact candidate JAR/image before replacing the API:

```bash
SPRING_PROFILES_ACTIVE=migrate java -jar target/fbo-manager-0.0.1-SNAPSHOT.jar
java -jar target/fbo-manager-0.0.1-SNAPSHOT.jar
```

The local Compose container intentionally compromises by using `fbo_app` as both database owner/migration identity and application identity. This keeps bootstrap reproducible without storing a second local superuser secret; the PostgreSQL 18 integration suite proves the separated shared-environment grants with distinct roles.

## Developer commands

Run these from `backend/` with JDK 25 active:

```bash
# Apply deterministic Java and text formatting.
./mvnw spotless:apply

# Check formatting without changing files.
./mvnw spotless:check

# Run all tests, including PostgreSQL 18 migration and data-access tests.
./mvnw test

# Run only the PostgreSQL 18 migration/data-access acceptance suite.
./mvnw -Dtest=PostgreSqlDataAccessIntegrationTests test

# Check formatting, test, verify module rules, and build the executable JAR.
./mvnw verify

# Rebuild from an empty target directory, as CI does.
./mvnw clean verify
```

The packaged application is `target/fbo-manager-0.0.1-SNAPSHOT.jar`.

## Ports and health

All default bindings are loopback-only for local development.

| Port | Binding | Purpose |
| --- | --- | --- |
| `5432` | `127.0.0.1` | PostgreSQL container |
| `8080` | `127.0.0.1` | Application/API traffic; no Actuator endpoints |
| `8081` | `127.0.0.1` | Private management server; only health is exposed |

Check the probes while the application and PostgreSQL are running:

```bash
curl --fail http://127.0.0.1:8081/actuator/health/liveness
curl --fail http://127.0.0.1:8081/actuator/health/readiness
```

Both return only `{"status":"UP"}` when healthy. Liveness measures process state and never checks PostgreSQL. Readiness combines Spring's readiness state with a bounded PostgreSQL `SELECT 1`; it returns HTTP `503` and only `{"status":"DOWN"}` when the database cannot serve work. Component names, exception details, URLs, usernames, and passwords are never returned.

`http://127.0.0.1:8080/actuator/health/liveness` returns `404`, which prevents accidental public exposure through the API listener.

## Configuration

`.env.example` contains safe local values and a password placeholder. Application configuration is immutable, typed, and validated at startup.

| Environment variable | Default/example | Meaning |
| --- | --- | --- |
| `FBO_ENVIRONMENT` | `local` | One of `local`, `ci`, `development`, `nonprod`, or `production` |
| `FBO_DATABASE_NAME` | `fbo_manager` | Compose-only PostgreSQL database name |
| `FBO_DATABASE_PORT` | `5432` | Compose-only loopback host port |
| `FBO_DATABASE_URL` | `jdbc:postgresql://localhost:5432/fbo_manager` | Credential-free PostgreSQL JDBC URL |
| `FBO_DATABASE_USERNAME` | `fbo_app` | Application and local container database user |
| `FBO_DATABASE_PASSWORD` | no safe default | Required secret; the application refuses to start when blank |
| `FBO_DATABASE_READINESS_TIMEOUT` | `3s` | Database probe timeout; greater than zero and at most five seconds |
| `FBO_DATABASE_MAXIMUM_POOL_SIZE` / `FBO_DATABASE_MINIMUM_IDLE` | `10` / `2` | Per-instance Hikari pool bounds |
| `FBO_DATABASE_CONNECTION_TIMEOUT` | `30000` | Hikari acquisition timeout in milliseconds |
| `FBO_MIGRATION_DATABASE_URL` | local application URL | Credential-free JDBC URL used only by the migration profile |
| `FBO_MIGRATION_DATABASE_USERNAME` / `FBO_MIGRATION_DATABASE_PASSWORD` | local application credential | Separate schema-owner credential in shared environments |
| `FBO_APPLICATION_DATABASE_ROLE` | `fbo_app` | Existing PostgreSQL role that receives application grants |
| `FBO_MIGRATIONS_ENABLED` | `false` | Emergency/test override; ordinary API startup never runs migrations |
| `FBO_AIRPORT_TIMEZONE` | `America/New_York` | IANA airport timezone exposed as a `ZoneId` bean |
| `FBO_LOG_LEVEL` | `INFO` | One of `DEBUG`, `INFO`, `WARN`, or `ERROR` |
| `FBO_ALLOWED_ORIGIN` | `http://localhost:5173` | Exact HTTP(S) browser origin without credentials or a path |
| `FBO_API_ADDRESS` / `FBO_API_PORT` | `127.0.0.1` / `8080` | Application listener |
| `FBO_MANAGEMENT_ADDRESS` / `FBO_MANAGEMENT_PORT` | `127.0.0.1` / `8081` | Distinct private management listener |
| `FBO_SHUTDOWN_TIMEOUT` | `30s` | Graceful shutdown timeout; greater than zero and at most 30 seconds |
| `FBO_BUILD_VERSION` | `dev` | Structured-log build version |
| `FBO_INSTANCE_ID` | `local` | Structured-log instance identifier |

Runtime business timestamps use the injected UTC `Clock`; local display and operating-day rules use the airport `ZoneId`. Do not use the host's default timezone for business logic.

## Logging and correlation IDs

Console output is one JSON object per event. Platform logs use allowlisted structured fields and do not emit request bodies, headers, query strings, database URLs, credentials, or exception messages from readiness failures.

Every request receives an `X-Request-Id` response header. A caller-supplied ID is accepted only when it contains 1–128 ASCII letters, digits, dots, underscores, colons, or hyphens; otherwise the application replaces it with a UUID. The ID is present in the logging context for the request and removed afterward.

## Module boundaries

Every capability and the narrow `platform` package is a closed Spring Modulith module. Each exposes only its `api` package as the named `api` interface.

| Module | Allowed cross-module APIs |
| --- | --- |
| `administration` | none |
| `aircraft` | none |
| `fleet` | none |
| `fuel` | `fleet::api`, `services::api`, `workforce::api` |
| `operations` | none |
| `parking` | none |
| `platform` | none |
| `services` | none |
| `tasks` | `fleet::api`, `workforce::api` |
| `visits` | `aircraft::api`, `parking::api` |
| `workforce` | none |

`ApplicationModuleStructureTests` verifies the complete graph and closed/named-interface declarations. Its isolated invalid fixture deliberately imports another module's internal type and proves that verification fails for the intended reason.

## Common failures

- **`release version 25 not supported` or Maven reports Java 21:** activate JDK 25 with `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` and rerun `./mvnw --version`.
- **Startup says `fbo.database.password` must not be blank:** copy `.env.example`, replace the password placeholder, and export `.env` before starting Maven or the JAR.
- **Compose reports that `FBO_DATABASE_PASSWORD` is required:** pass `--env-file .env` and ensure the password has a value.
- **Port 5432, 8080, or 8081 is already allocated:** stop the conflicting process or change the matching environment variable. Keep API and management bindings distinct.
- **Docker cannot connect to the daemon:** start Docker Desktop/Engine, then rerun the Compose command.
- **Liveness is up but readiness is down:** inspect `docker compose --env-file .env ps`, verify PostgreSQL is healthy, and confirm the JDBC URL, user, password, and mapped port agree.
- **Startup reports a missing table or Hibernate validation error:** run `SPRING_PROFILES_ACTIVE=migrate ./mvnw spring-boot:run` with migration credentials, then restart the ordinary application. Do not enable Flyway on a shared API instance.
- **Flyway says the application role does not exist:** create the shared-environment application login through the platform's secret/identity process and set `FBO_APPLICATION_DATABASE_ROLE` to that simple lowercase PostgreSQL identifier.
- **`spotless:check` fails:** run `./mvnw spotless:apply`, review the formatting changes, and rerun `./mvnw verify`.
- **The management endpoint is unreachable from another host:** loopback-only is the safe local default. Production deployment must explicitly set a private management address and protect it at the network boundary.

CI uses the same committed wrapper, JDK 25, pinned PostgreSQL image, `clean verify`, the one-shot migration profile, and live liveness/readiness smoke checks. Local completion evidence is retained in [Issue 10 verification](docs/issue-10-verification.md) and [Issue 11 verification](docs/issue-11-verification.md).
