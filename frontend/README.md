# FBO Manager frontend

The MVP frontend is an authenticated React and TypeScript single-page application for airport operations. Issue [#30](https://github.com/ecillie/FBO_Manager/issues/30) defines its architecture without implementing screens.

The accepted technology, feature boundaries, state ownership, API-client strategy, routing conventions, accessibility requirements, responsive behavior, and rejected alternatives are recorded in [ADR 0001](../docs/architecture/decisions/0001-frontend-application-architecture.md). The shared HTTP/JSON contract, idempotency behavior, dashboard projection, and polling decision are defined by [ADR 0003](../docs/architecture/decisions/0003-api-contracts-and-operational-data-flows.md) and the detailed [API contracts and operational data flows](../docs/architecture/api-contracts-and-data-flows.md). [ADR 0004](../docs/architecture/decisions/0004-delegated-identity-and-capability-authorization.md) and the [security architecture](../docs/architecture/security.md) define the backend-owned session, CSRF, capability, and browser-storage boundary. [ADR 0007](../docs/architecture/decisions/0007-layered-verification-and-immutable-promotion.md) defines frontend/component/browser checks and immutable artifact promotion.

## Architecture summary

- React 19.2 and TypeScript 6, built with Vite 8.1 on Node.js 24 LTS.
- pnpm 11 with an exact package-manager version and committed lockfile.
- React Router 8 for URL and navigation state.
- TanStack Query 5 for backend state, caching, polling, and invalidation.
- React Hook Form and Zod for unsaved form state and immediate validation.
- Material UI 9 and the MUI X Community Data Grid for the component baseline.
- `openapi-typescript` and `openapi-fetch` for the versioned backend contract.

Operational state remains authoritative in the backend and PostgreSQL. The browser does not optimistically complete parking, visit, dispatch, task, service, or fuel operations and does not automatically retry mutations.

## Planned source boundaries

Application code will be grouped into `app`, `api`, `features`, and `shared`. The feature directories are:

- `ramp-operations`;
- `aircraft-customers`;
- `parking`;
- `services`;
- `fleet-fuel`;
- `workforce`;
- `tasks`; and
- `administration`.

Each feature exposes a public entry point. Cross-feature pages compose those public APIs, while shared code remains domain-neutral. Generated API files are never edited by hand.

The frontend will be scaffolded by an implementation ticket after the backend contract location and security integration points are available. Until then, this directory intentionally contains documentation only.
