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

The current dependency set is deliberately small: Spring MVC, validation, Actuator, Spring Modulith, structured logging supplied by Spring Boot, and the runtime PostgreSQL driver used by readiness. Persistence, Flyway, and application workflows are added by their follow-up tickets.

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

Export the application settings and run the service:

```bash
set -a
source .env
set +a

./mvnw spring-boot:run
```

The application rejects missing or invalid configuration during startup. Stop it with `Ctrl+C`; graceful shutdown refuses new traffic and allows active requests up to the configured timeout.

Stop PostgreSQL while preserving its local data:

```bash
docker compose --env-file .env down
```

To intentionally erase the local database and recreate it from scratch:

```bash
docker compose --env-file .env down --volumes
```

The second command permanently deletes the Compose-managed local database volume.

## Developer commands

Run these from `backend/` with JDK 25 active:

```bash
# Apply deterministic Java and text formatting.
./mvnw spotless:apply

# Check formatting without changing files.
./mvnw spotless:check

# Run all tests, including module-boundary and health-contract tests.
./mvnw test

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
- **`spotless:check` fails:** run `./mvnw spotless:apply`, review the formatting changes, and rerun `./mvnw verify`.
- **The management endpoint is unreachable from another host:** loopback-only is the safe local default. Production deployment must explicitly set a private management address and protect it at the network boundary.

CI uses the same committed wrapper, JDK 25, pinned PostgreSQL image, `clean verify`, and live liveness/readiness smoke checks. Local completion evidence is retained in [Issue 10 verification](docs/issue-10-verification.md).
