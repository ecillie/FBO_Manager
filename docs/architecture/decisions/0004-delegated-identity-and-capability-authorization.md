# ADR 0004: Delegated identity and capability-based authorization

- **Status:** Accepted
- **Date:** 2026-08-09
- **Decision owners:** FBO Manager architecture contributors
- **Tracking issue:** [#33](https://github.com/ecillie/FBO_Manager/issues/33)
- **Implementation issue:** [#13](https://github.com/ecillie/FBO_Manager/issues/13)

## Context

FBO Manager handles personal, workforce, operational, and fuel-inventory information. Every application user must resolve to an active `workers` record, clients must not grant their own authority, and security-sensitive actions must be attributable to a named worker. The MVP team should not create and operate password storage, recovery, MFA, and account-protection infrastructure when an approved standards-based identity provider can own those controls.

The browser is an untrusted client. Persisting reusable bearer tokens in browser-readable storage would enlarge the impact of cross-site scripting, while per-request provider calls would make the identity provider an unnecessary availability dependency.

## Decision

Use an FBO-approved OpenID Connect provider for primary authentication. The Spring backend is the confidential OIDC client and uses Authorization Code flow with PKCE, state, and nonce. It validates provider tokens, resolves exact `(issuer, sub)` identity links to active workers, then discards the provider tokens. There is no self-registration or email-based linking.

The backend creates a revocable opaque application session stored as a keyed hash in PostgreSQL. The browser receives only a `Secure`, `HttpOnly`, `SameSite=Lax`, host-only cookie. Sessions have an eight-hour idle timeout and 12-hour absolute lifetime, rotate after security-sensitive events and periodically, and never carry authoritative roles. Unsafe requests also require same-origin and session-bound CSRF validation.

Authorize in backend application services with stable capabilities loaded from the worker's current server-side role. Seed least-privilege `ADMINISTRATOR`, `DISPATCHER`, `FUEL_MANAGER`, `WORKFORCE_MANAGER`, `OPERATOR`, and `VIEWER` roles. Audit authentication, authorization denials, override use, fuel movement and adjustment, identity/role administration, session revocation, and configuration changes in append-only application audit records distinct from diagnostic logs.

Bootstrap the first administrator through a one-time, non-HTTP command that is restricted to the protected runtime, exact OIDC issuer/subject, and an empty-administrator precondition. It creates the worker, identity link, role assignment, and audit event atomically, then is disabled.

The detailed lifecycle, capability matrix, browser controls, data handling, audit fields, bootstrap sequence, and threat model are normative in [Security architecture and threat model](../security.md).

## Consequences

### Benefits

- FBO Manager stores no staff passwords, reset secrets, recovery codes, or browser-readable bearer tokens.
- Provider MFA, credential policy, and account recovery can follow the FBO's existing identity governance.
- Server-side sessions support immediate local revocation and live worker/capability changes without a provider call on every API request.
- Exact subject linking and server-derived actor context preserve accountability across operational and inventory transactions.
- Stable capabilities keep authorization rules independent of presentation behavior and customizable role names.

### Costs and risks

- Shared environments require a correctly configured, available OIDC provider for new login.
- Application sessions and identity links require new migration-managed tables and cleanup/revocation logic.
- Provider account disablement is not instant local revocation unless an administrator also deactivates the worker or a future provider event integration is added; the 12-hour absolute lifetime bounds this MVP risk.
- Cookie authentication requires explicit CSRF and same-origin controls.
- The first-administrator command and total-admin-loss recovery need protected operator access and disciplined auditing.

## Rejected alternatives

| Alternative | Reason not selected for the MVP |
| --- | --- |
| Local username/password authentication | It makes the small MVP team responsible for password hashing policy, MFA, reset/recovery, breach monitoring, lockout, and credential support. It may be reconsidered only if the pilot FBO cannot approve an OIDC provider. |
| OIDC tokens stored in `localStorage` or IndexedDB | Browser script compromise would expose reusable bearer credentials, and robust revocation would be harder. |
| Pure stateless JWT authorization | Worker activation and role changes would remain stale until token expiry, logout would not revoke immediately, and claims would couple provider configuration to application capabilities. |
| Provider call on every API request | It adds latency, rate-limit exposure, and an external availability dependency without improving server-side domain authorization. |
| Email-address identity mapping or automatic just-in-time provisioning | Email is mutable and may be reassigned; automatic provisioning could grant application presence without deliberate workforce administration. |
| One hard-coded role per endpoint | It prevents least-privilege site-specific roles and scatters policy across controllers. Stable capabilities are the enforcement vocabulary. |
| Shared administrator account or committed bootstrap credential | It removes individual accountability and creates a reusable high-value secret. |

## Compliance and revision

Issue #13 implements this decision and its automated boundary tests. Issue #26 enforces secret/dependency/source/artifact controls. Security-sensitive API operations remain subject to [ADR 0003](0003-api-contracts-and-operational-data-flows.md), including safe errors, request IDs, transaction ownership, and idempotency.

A change to local credentials, browser token storage, stateless authorization, identity linking, session lifetime, first-admin bootstrap, or capability semantics requires a superseding ADR plus updates to the security threat model, OpenAPI security schemes, deployment secrets, operating runbooks, and tests.
