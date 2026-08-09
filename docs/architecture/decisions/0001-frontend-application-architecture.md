# ADR 0001: Frontend application architecture and state boundaries

- **Status:** Accepted
- **Date:** 2026-08-09
- **Decision owners:** FBO Manager architecture contributors
- **Tracking issue:** [#30](https://github.com/ecillie/FBO_Manager/issues/30)
- **Related issues:** [#12](https://github.com/ecillie/FBO_Manager/issues/12), [#24](https://github.com/ecillie/FBO_Manager/issues/24), [#32](https://github.com/ecillie/FBO_Manager/issues/32), and [#33](https://github.com/ecillie/FBO_Manager/issues/33)

## Context

FBO Manager needs a browser application for data-dense airport operations. It must support administrators, dispatchers, CSRs, line-service workers, fuelers, managers, and read-only users without becoming an independent source of operational truth.

The frontend must make current work easy to understand while preserving the system constraints in the [MVP architecture](../../architecture.md), [workflow definitions](../mvp-scope-and-workflows.md), and [quality attributes](../quality-attributes.md). In particular:

- PostgreSQL and the backend API remain authoritative.
- The frontend may perform usability validation, but the backend repeats authorization, validation, and concurrency checks.
- Parking, visit, task, dispatch, and fuel mutations are not successful until the backend transaction commits.
- The current-state ramp board must reflect committed state within 15 seconds under the MVP polling baseline.
- Core browser workflows must meet WCAG 2.2 AA expectations.
- A future native client will reuse the backend contract but is not part of this decision.

[ADR 0003](0003-api-contracts-and-operational-data-flows.md) now defines the exact HTTP conventions and operational data flows. Issues #12 and #24 implement and publish that contract; this ADR defines the frontend side of the same boundary.

## Decision

### Runtime and core technology

The MVP frontend will be a client-rendered single-page application. It will be compiled into static assets and communicate with the separately deployed backend over HTTPS/JSON. Server-side rendering, React Server Components, and a frontend-specific application server are not part of the MVP.

| Concern | Selection | Version policy |
| --- | --- | --- |
| Production runtime | Modern web browser | Supported baseline below |
| Build and development runtime | Node.js 24 LTS | Pin the 24.x line in the developer and CI environment |
| UI framework | React 19.2 | Require at least 19.2.7 for React Router 8 compatibility; pin an exact patch in the lockfile |
| Language | TypeScript 6 in strict mode | Pin an exact 6.0.x compiler version |
| Build tool | Vite 8.1 | Client SPA mode; pin an exact patch |
| Package manager | pnpm 11 | Pin the exact CLI through `packageManager` and commit `pnpm-lock.yaml` |
| Routing | React Router 8 Data Mode | Browser-history routing with lazy feature route modules |
| Server-state cache | TanStack Query 5 | Queries and mutations wrap the generated API contract |
| Form state and validation | React Hook Form plus Zod | Forms own drafts; schemas provide immediate usability validation |
| Component system | Material UI 9 | One application theme and shared accessible primitives |
| Data grids | MUI X Data Grid Community | MIT-licensed features only unless a later decision approves a commercial tier |
| API contract integration | `openapi-typescript` plus `openapi-fetch` | Generated types with a small typed runtime client |
| Unit and component testing | Vitest, Testing Library, user-event, and MSW | Test behavior and API boundaries rather than component internals |
| Browser workflow testing | Playwright plus axe accessibility checks | Run representative Chromium, Firefox, and WebKit journeys |

Production dependencies and the package-manager version will be exact-pinned when the frontend is scaffolded. Dependabot or an equivalent later maintenance decision may prepare upgrades, but major-version changes require review against this ADR. CI uses a frozen lockfile install.

### Supported browsers

The initial support baseline follows the stricter of Vite 8 and Material UI 9 requirements:

- Chrome 117 or newer;
- Microsoft Edge 121 or newer;
- Firefox 121 or newer;
- Safari 17 or newer on macOS and iOS; and
- no Internet Explorer or legacy non-module browser support.

The production build target will record these minimums explicitly. Playwright exercises the current Chromium, Firefox, and WebKit engines. The minimum versions are reviewed when Vite or Material UI changes major version and before a pilot airport standardizes managed devices.

### Source organization and dependency direction

Source code will be organized by operational feature instead of by technical artifact type. The intended shape is:

```text
frontend/
  src/
    app/                 # bootstrap, providers, router, layouts, error boundaries
    api/
      generated/         # generated OpenAPI types; never edited by hand
      client/            # transport, middleware, and normalized API errors
    features/
      ramp-operations/
      aircraft-customers/
      parking/
      services/
      fleet-fuel/
      workforce/
      tasks/
      administration/
    shared/
      ui/                # themed, application-level UI primitives
      formatting/        # time, date, quantity, and identifier formatting
      validation/        # truly cross-feature client validation helpers
      testing/           # shared render, fixture, and MSW utilities
```

Each feature may contain `api`, `components`, `forms`, `hooks`, `routes`, `schemas`, and feature-local tests. A feature exposes an explicit public entry point. Other features must not import its internal files.

Dependency direction is:

```text
app -> feature public APIs -> api client and shared utilities
```

`shared` contains only domain-neutral building blocks used by multiple features. It must not become a miscellaneous location for feature logic. `app` composes features but does not implement their domain behavior. Generated API types may be referenced by feature API adapters, but presentation components should consume feature-facing view types where API shapes are unsuitable for display.

The client must not reproduce backend transaction rules as authoritative domain services. Client validation exists for quick feedback and disabled-state explanations; the backend response decides whether an operation succeeds.

### Feature boundaries

| Feature | Owns | Does not own |
| --- | --- | --- |
| Ramp operations | Current operations board, active visit orchestration, arrival/departure interactions, and cross-feature status presentation | Authoritative parking, service, task, or fuel rules |
| Aircraft and customers | Customer, manufacturer, model, physical-aircraft, classification, and aircraft-history interfaces | Active ramp coordination or parking availability |
| Parking | Area hierarchy, spots, preferences, availability presentation, assignment interaction, and parking history | Visit lifecycle transactions or hidden occupancy flags |
| Services | Service-request creation, queues, lifecycle presentation, and service history | Task resource locking or fuel-ledger writes |
| Fleet and fuel | Service vehicles, fuel trucks, tanks, holder balances, transfers, dispenses, adjustments, and immutable ledger history | Worker eligibility or independent balance calculations that replace server values |
| Workforce | Workers, schedules, shifts, attendance, eligibility presentation, and derived availability | Authentication-provider implementation or task lifecycle ownership |
| Tasks | Airport and aircraft tasks, worker/vehicle assignments, dispatch queues, and task transitions | Worker, vehicle, service, or visit master data |
| Administration | Airport settings, reference catalogs, role/capability administration, and feature-level configuration entry points | Duplicated CRUD implementations for entities owned by another feature |

Cross-feature pages use composition rather than direct internal imports. For example, ramp operations may present parking and task summaries returned by a dashboard endpoint, while the parking and tasks features own their detailed interactions and cache keys.

### State ownership

State is classified before choosing where to store it:

| State class | Owner and mechanism | Examples and rules |
| --- | --- | --- |
| Server state | TanStack Query | API records, lists, current-state views, histories, permissions returned with resources; never copied into a general client store |
| URL and navigation state | React Router params and validated search parameters | Selected date, visit, tab, filters, sorting, and pagination when a refresh or shared link should preserve them |
| Form state | React Hook Form with Zod usability validation | Unsaved field values, touched/dirty state, and client validation; reset from a confirmed server result after success |
| Authentication state | An application auth provider behind an adapter owned by issue #33 | Bootstrap status, current identity, session status, and server-issued capabilities; token storage and renewal follow the security ADR |
| Transient UI state | Component-local `useState` or `useReducer` | Dialog visibility, disclosure state, temporary selection, and hover/focus interactions |
| Theme and application chrome | Narrow React contexts | Theme preference, notification dispatch, and other cross-cutting presentation services |

Redux, Zustand, and other general global stores are not included in the MVP baseline. They may be introduced only when a documented client-owned workflow cannot be expressed cleanly through URL, form, local, authentication, or server state. Server responses must never be mirrored into such a store merely for convenient access.

Persistent browser storage is not an operational datastore. It may retain non-sensitive presentation preferences such as table density. It must not retain fuel transactions, visit drafts, authorization claims, access tokens, or an offline write queue. The authentication decision in issue #33 determines whether any credential material may be stored by the browser.

### API client and generated contract

Issue #24 will publish the canonical machine-readable OpenAPI contract at a repository-managed, versioned path. The agreed consumer layout is:

```text
contracts/openapi/v1.yaml                 # canonical contract produced or validated by the backend
frontend/src/api/generated/v1.d.ts        # generated TypeScript contract types
frontend/src/api/client/                  # handwritten typed transport and error normalization
```

The canonical schema, generated types, generator version, and `pnpm-lock.yaml` are committed. Generated files include a do-not-edit header and the source contract path. `pnpm api:generate` will run `openapi-typescript` using the pinned local dependency; it must not depend on a globally installed generator or a running backend.

CI will:

1. lint or validate the canonical OpenAPI document using the rule set selected by issue #24;
2. regenerate `frontend/src/api/generated/v1.d.ts`;
3. fail if regeneration changes the committed file;
4. type-check the frontend with `tsc --noEmit`; and
5. run contract-facing tests against representative success and error responses.

`openapi-fetch` provides the only low-level HTTP client. It is configured once with the API base URL and cross-cutting middleware selected by issues #12, #32, and #33, including session credentials, request identifiers, safe error normalization, and idempotency headers where required. Feature modules wrap typed paths in query and mutation option factories; components do not call `fetch` directly.

Non-breaking additions remain under `v1`. A breaking contract moves to a new API version and a new generated namespace rather than silently replacing v1 types. Generated types are committed so a frontend checkout remains reproducible and contract changes are visible during review.

### Query keys, caching, and refresh

Each feature owns typed query-key and query-option factories. Keys include the feature, resource, stable identifier, and normalized list inputs as applicable. For example:

```text
['parking', 'spots', { areaId, status, page, sort }]
['ramp-operations', 'board', { operatingDate }]
['tasks', 'detail', taskId]
```

Objects used in keys must be normalized so omitted defaults and explicit defaults do not create different cache entries. Components use feature query factories rather than constructing ad hoc keys.

The initial freshness profiles are:

| Data profile | Stale time | Automatic refresh |
| --- | ---: | --- |
| Active ramp board and operational queues | 5 seconds | Poll every 10 seconds while the document is visible; refresh on focus and reconnect |
| Active visit, parking, task, worker, vehicle, and fuel-holder detail | 15 seconds | Refresh on focus/reconnect and after relevant mutations |
| Configuration and reference catalogs | 5 minutes | Refresh on focus when stale and after administration mutations |
| Retained history | 1 minute | No interval polling; refresh after a related successful mutation |

Polling pauses for hidden documents and resumes immediately when the document becomes visible. This is the MVP transport selected by [ADR 0003](0003-api-contracts-and-operational-data-flows.md) and meets the 15-second dashboard-freshness target. Server-sent events and WebSockets are deferred; TanStack Query remains the normalized read cache.

### Mutation, invalidation, retry, and optimistic-update policy

Successful mutations use the response to update an exact detail entry when it is complete, then invalidate only affected collection, aggregate, and current-state keys. Broad cache invalidation from the root is prohibited because it creates unnecessary traffic and hides ownership. Feature mutation definitions document their invalidation effects.

Read queries retry at most twice for transient network failures, HTTP 408, HTTP 429 when allowed by the response, and HTTP 5xx. Retries use capped exponential backoff with jitter and honor `Retry-After`. Reads do not retry authentication, authorization, not-found, validation, or conflict responses.

Mutations do not retry automatically. An explicit user retry may reuse the same idempotency key when the previous outcome is unknown and the operation is marked idempotent under [ADR 0003](0003-api-contracts-and-operational-data-flows.md). A new user intent receives a new key. The client must not invent idempotency for an endpoint that does not guarantee it.

Operational writes use server-confirmed updates. The frontend must not optimistically claim success for:

- visit arrival, departure, cancellation, or status transitions;
- parking assignments or releases;
- task starts, completion, or worker/vehicle claims;
- service lifecycle transitions; or
- fuel receipts, transfers, dispenses, or adjustments.

Optimistic updates are limited to reversible, non-operational presentation preferences, such as a locally stored table-density choice. This policy favors clear pending states and conflict handling over a briefly faster but potentially false display.

### Routing and layouts

React Router uses browser history through `createBrowserRouter`. The deployment must route unknown non-asset paths to `index.html`; hash routing is not used. Route modules are lazy-loaded at feature boundaries.

The initial route families are:

```text
/operations
/aircraft
/customers
/parking
/services
/fleet
/fuel
/workforce
/tasks
/administration
```

Record identifiers are route parameters. Shareable filters, dates, tabs, sorts, and pages are URL search parameters validated at the route boundary. Sensitive values and unsaved form contents do not belong in the URL.

The layout hierarchy is:

1. bootstrap providers for configuration, API, query cache, authentication, theme, and notifications;
2. a root error boundary and authentication boundary;
3. the authenticated application shell with skip link, primary navigation, current-user controls, and connection/freshness indicators;
4. capability-aware feature navigation and a feature layout; and
5. the page or record layout with its own loading and error boundaries where useful.

Client route guards improve navigation and explain unavailable capabilities, but the backend remains responsible for authorization.

### Loading, empty, and error conventions

Loading, empty, error, stale, refreshing, and success are distinct states:

- Initial page loads use structure-preserving skeletons or an accessible progress status near the affected content.
- Background refresh leaves confirmed content visible and shows a non-blocking freshness indicator.
- Mutations disable only conflicting controls, expose a pending label, and prevent duplicate submission.
- Empty collections explain what is absent and, when authorized, offer the next valid action.
- Filtered-empty results state that filters removed all matches and offer a clear-filter action.
- Lack of permission is not presented as an empty collection.
- Expected field validation stays adjacent to the field and is summarized at form level when submission fails.
- Conflict responses preserve the user's context, explain that authoritative state changed, and offer refresh/review rather than silently overwriting.
- Route-level failures provide a retry or safe navigation action. The root boundary provides recovery navigation and a request identifier when available.

Client-visible errors never include raw SQL, tokens, stack traces, or unredacted response bodies. Authentication expiry, forbidden access, not-found routes, validation failures, conflicts, and unavailable dependencies receive separate handling based on the stable error contract selected by issues #12 and #32.

### Accessibility

Core workflows target WCAG 2.2 AA. The implementation requirements are:

- use semantic HTML before ARIA and preserve a logical heading structure;
- make all actions, dialogs, menus, tables, and grid interactions keyboard operable;
- provide a visible focus indicator and restore focus after dialogs and route transitions;
- announce asynchronous status changes without repeatedly interrupting assistive technology;
- associate errors and instructions with form controls and never rely on color alone;
- maintain at least the required text and non-text contrast in every supported theme;
- respect reduced-motion, text zoom, and browser zoom preferences;
- provide touch targets appropriate for supported tablet use; and
- test critical journeys with axe plus manual keyboard and screen-reader review.

MUI accessibility behavior is a starting point, not evidence that composed application workflows conform automatically. Custom cell renderers and operational status presentations require explicit keyboard and accessible-name review.

### Responsive layout

The browser application is responsive but does not attempt to become the deferred native mobile application. MUI breakpoints provide the baseline layout bands:

- compact: below 600 px;
- medium: 600 through 1199 px; and
- wide: 1200 px and above.

Core workflows are verified at representative 390 px, 768 px, and 1280 px viewports. Wide operational boards may use intentional horizontal scrolling with visible labels or a task-focused compact presentation; controls must not be made unusably small merely to avoid scrolling. Navigation collapses at compact widths, dialogs remain within the viewport, and primary actions remain reachable without hover.

### Time, dates, and quantities

The frontend uses the configured airport IANA timezone for operational display and UTC instants at the API boundary.

- API timestamps are parsed only as the RFC 3339 UTC instants defined by [ADR 0003](0003-api-contracts-and-operational-data-flows.md).
- Operational timestamps are formatted through a shared `Intl.DateTimeFormat` service with the airport `timeZone`; components do not call locale formatting ad hoc.
- Displays identify the airport-local context, including a timezone abbreviation or explicit label where confusion is plausible.
- Date-only values remain `YYYY-MM-DD` calendar values and are not converted through a UTC instant.
- Airport-local schedule entry is converted at a single boundary and must handle nonexistent or ambiguous daylight-saving times explicitly.
- Relative labels such as “5 minutes ago” supplement, rather than replace, an accessible exact timestamp.

Fixed-precision domain quantities are transported and held as decimal strings with an explicit controlled unit. They are not converted to JavaScript `number` values. Formatting uses shared quantity helpers and `Intl.NumberFormat`; arithmetic, if a UI genuinely needs it, uses a decimal library and never replaces the backend's authoritative calculation. The frontend performs no automatic unit conversion in the MVP. Fuel balances outside nominal range remain visible and are not clamped.

Identifiers and natural codes are displayed in their canonical form. Locale-aware formatting is presentation-only and never changes API payloads, query keys, or comparison behavior.

## Consequences

### Benefits

- A static SPA keeps deployment aligned with the single-backend MVP architecture.
- Feature boundaries match operational responsibilities and support independent route loading and testing.
- State ownership prevents remote records from drifting across multiple client stores.
- Typed OpenAPI integration makes backend contract changes visible during generation and type checking.
- Server-confirmed mutations align the UI with transactional and concurrency-sensitive workflows.
- MUI supplies consistent accessible primitives and a capable community data grid for data-dense screens.

### Costs and risks

- React is deliberately modular, so the ADR and automated boundary checks must prevent inconsistent local patterns.
- MUI adds runtime and styling weight; imports, route splitting, and bundle budgets must be monitored.
- The MUI X Community tier may not include every desired grid behavior. A commercial tier requires a separate cost and licensing decision.
- Committed generated types add review noise, but they make contract drift and upgrade effects explicit.
- Polling creates repeated reads. Query scoping and database indexes must keep the selected 10-second operational refresh inexpensive.
- Browser-only operation provides no offline writes; this is an accepted MVP non-goal.

## Rejected alternatives

| Alternative | Reason not selected for the MVP |
| --- | --- |
| Next.js or React Router Framework Mode with SSR | Public SEO and server-rendered content are not requirements. A frontend server would add deployment, caching, and trust-boundary complexity beside the authoritative backend. |
| Angular | Its integrated conventions are credible for a larger enterprise team, but its additional framework and RxJS surface is not justified for the current MVP or scale. |
| Vue or Svelte | Both can satisfy the product, but React has the selected component, query, routing, and testing ecosystem and provides no weaker fit for the documented constraints. |
| Redux Toolkit as the default state container | Most shared state is remote server state or URL state. Copying it into Redux would create duplicate cache and invalidation responsibilities. |
| Zustand as a default global store | A general client store is unnecessary until a concrete cross-feature client-owned state problem exists. |
| Next.js server actions or direct database access | They would blur the defined browser/backend boundary and bypass the shared API intended for the future native client. |
| Raw `fetch` calls in components | They duplicate authentication, error, cancellation, typing, retry, and cache behavior and make contract use inconsistent. |
| A large generated SDK or generated TanStack Query hooks | Generated transport opinions make query keys and invalidation harder to own by feature. Generated schema types plus a small typed client preserve contract safety with explicit cache behavior. |
| Tailwind plus individually assembled headless components | This provides visual flexibility but shifts more accessibility, theming, and data-grid composition work into the MVP. MUI is a faster fit for an operational application. |
| MUI X Pro or Premium | The MVP has not established a need that justifies a commercial runtime license. |
| Offline-first storage or a service-worker write queue | Offline writes conflict with the MVP's authoritative transactional backend and are explicitly outside scope. |
| WebSockets by default | [ADR 0003](0003-api-contracts-and-operational-data-flows.md) selects bounded polling because it meets the 15-second freshness target; push transport requires later measured evidence and a new decision. |

## Compliance and revision

The frontend scaffold and reviews must verify that:

- dependency and package-manager versions are pinned;
- imports respect feature public boundaries;
- components do not call the backend outside the typed API layer;
- operational mutations have no optimistic success state or automatic retry;
- generated OpenAPI types are current;
- core routes cover loading, empty, unauthorized, conflict, and error behavior;
- supported viewports and browser engines pass critical workflows; and
- automated accessibility results are supplemented by manual review.

A later decision may revise a technology or boundary when implementation evidence shows that the current choice fails a quality target. The superseding ADR must identify migration impact, affected contracts, and whether the supported-browser or deployment baseline changes.
