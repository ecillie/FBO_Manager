# Issue 10 verification

Local completion evidence captured on 2026-08-11 in `America/New_York`. The committed CI workflow repeats the clean build and healthy-database probe checks after checkout; no CI run URL exists until this branch is pushed.

## Toolchain

```text
$ ./mvnw --version
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Java version: 25.0.4, vendor: Eclipse Adoptium
OS name: "mac os x", version: "26.5.2", arch: "aarch64"

$ docker version --format 'Docker client={{.Client.Version}} server={{.Server.Version}}'
Docker client=28.3.2 server=28.3.2

$ docker compose version
Docker Compose version v2.38.2-desktop.1
```

## Formatting, tests, module verification, and package

```text
$ ./mvnw spotless:apply clean verify
Spotless.Format is keeping 5 files clean
Spotless.Java is keeping 40 files clean
Compiling 28 source files with javac [debug parameters release 25]
Compiling 12 source files with javac [debug parameters release 25]
ApplicationModuleStructureTests: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
Building jar: backend/target/fbo-manager-0.0.1-SNAPSHOT.jar
BUILD SUCCESS
```

The three module tests verify the real module graph, assert that all 11 modules are closed and expose a named `api`, and prove that an isolated fixture which imports another module's internal type fails with a Modulith `Violations` exception.

## Pinned PostgreSQL workflow

A native PostgreSQL process already occupied the documented default port 5432, so the verification used isolated port 55432 and a ticket-only Compose project. The committed default remains 5432.

```text
$ docker compose --env-file .env.example config --quiet
exit 0

$ FBO_DATABASE_PORT=55432 docker compose -p fbo-manager-ticket10-verify --env-file .env.example up --detach --wait postgres
Container fbo-manager-ticket10-verify-postgres-1 Healthy

$ docker compose -p fbo-manager-ticket10-verify --env-file .env.example ps --format json
Image: postgres:18.3-alpine@sha256:54451ecb8ab38c24c3ec123f2fd501303a3a1856a5c66e98cecf2460d5e1e9d7
State: running
Health: healthy
Ports: 127.0.0.1:55432->5432/tcp
```

## Live health behavior

The packaged JAR was run with the isolated JDBC URL and a local test credential:

```text
$ FBO_DATABASE_URL=jdbc:postgresql://127.0.0.1:55432/fbo_manager FBO_DATABASE_PASSWORD=<local-test-value> java -jar target/fbo-manager-0.0.1-SNAPSHOT.jar
Started FboManagerApplication using Java 25.0.4
event=application.ready airportTimezone=America/New_York apiPort=8080 managementPort=8081
```

Healthy database:

```text
$ curl http://127.0.0.1:8081/actuator/health/liveness
{"status":"UP"} HTTP 200

$ curl http://127.0.0.1:8081/actuator/health/readiness
{"status":"UP"} HTTP 200

$ curl http://127.0.0.1:8080/actuator/health/liveness
HTTP 404
```

Database stopped while the application remained running:

```text
$ docker compose -p fbo-manager-ticket10-verify --env-file .env.example stop postgres
Container fbo-manager-ticket10-verify-postgres-1 Stopped

$ curl http://127.0.0.1:8081/actuator/health/liveness
{"status":"UP"} HTTP 200

$ curl http://127.0.0.1:8081/actuator/health/readiness
{"status":"DOWN"} HTTP 503

event=dependency.health.failed dependency=postgresql operation=readiness outcome=failure
```

Database recovered:

```text
$ FBO_DATABASE_PORT=55432 docker compose -p fbo-manager-ticket10-verify --env-file .env.example up --detach --wait postgres
Container fbo-manager-ticket10-verify-postgres-1 Healthy

$ curl http://127.0.0.1:8081/actuator/health/readiness
{"status":"UP"} HTTP 200

event=dependency.health.recovered dependency=postgresql operation=readiness outcome=success
```

The transition logs contained no JDBC URL, username, password, or exception message. Interrupting the application emitted one custom `application.shutdown.started` event and Spring Boot completed graceful shutdown for both the API and management web servers.

## Cleanup

```text
$ FBO_DATABASE_PORT=55432 docker compose -p fbo-manager-ticket10-verify --env-file .env.example down --volumes
Container fbo-manager-ticket10-verify-postgres-1 Removed
Volume fbo-manager-ticket10-verify_postgres-data Removed
Network fbo-manager-ticket10-verify_default Removed
```

Only the ticket-created container, network, and synthetic volume were removed. The pre-existing native PostgreSQL instance and its data were not touched.
