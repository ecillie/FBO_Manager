# ADR 0008: Environment-aligned integration and release promotion branches

- **Status:** Accepted
- **Date:** 2026-08-09
- **Decision owner:** FBO Manager project owner
- **Tracking issue:** [#69](https://github.com/ecillie/FBO_Manager/issues/69)
- **Implementation issue:** [#26](https://github.com/ecillie/FBO_Manager/issues/26)
- **Amends:** Branch, review, artifact-source, and promotion portions of [ADR 0007](0007-layered-verification-and-immutable-promotion.md)

## Context

FBO Manager has one developer and three repository branches established for delivery:

- `FBODev` for integrated development work;
- `Release-1.0.0` for the MVP release candidate; and
- `FBOProd` as the default branch and production code record.

The earlier release decision assumed ordinary pull requests merged to `main` and artifacts built from that branch. That branch no longer exists on GitHub. The repository needs an explicit flow that prevents routine changes from reaching the production branch, avoids three independently evolving code lines, and still promotes the exact artifacts tested in non-production.

The single-developer constraint also makes a mandatory second-developer pull-request approval impossible. Required automation and a review record must remain strong without encoding an unsatisfiable branch rule.

## Decision

### Ticket integration

Ordinary work starts from current `FBODev` and uses a short-lived branch named `<issue-number>-<short-description>`, for example `10-bootstrap-backend`. Ticket pull requests explicitly target `FBODev`; `FBOProd` being the GitHub default must not silently change the base. Ticket branches use squash merge and are deleted after merge.

Because GitHub closing keywords take effect only when a pull request reaches the default branch, a merge to `FBODev` does not automatically close its linked issue. After verifying the integration result, the developer or issue #26 automation records merge evidence, closes the implementation issue, and marks its project item Done. Release epics separately track whether that code has promoted to production.

`FBODev` is the only continuing integration line. It deploys to the development environment after its required checks pass. Neither the current release branch nor `FBOProd` receives routine feature pull requests.

### Release candidate

`Release-1.0.0` is cut from one approved `FBODev` commit for the MVP. Future candidates use `Release-<version>`. After the cut, the release branch accepts only reviewed fixes required to pass release acceptance; unrelated work continues on `FBODev`.

Each release fix is also forward-ported to `FBODev` before the release closes. This prevents a candidate-only correction from disappearing from future development.

A successful candidate commit builds the web/API images, migration command, OpenAPI/client artifacts, SBOMs, provenance, checksums, and release manifest once. Those signed exact digests deploy to the NonProd acceptance environment. Repeated verification does not rebuild them.

### Production promotion

After NonProd acceptance and the production preflight, a promotion pull request merges the accepted `Release-1.0.0` tree into `FBOProd`. The resulting production commit is tagged `v1.0.0`. Production deploys the candidate digests recorded in the accepted release manifest; it does not rebuild from the promotion merge commit.

`FBOProd` is the production record and GitHub default branch. Default status does not make it the ordinary pull-request target. Direct and force pushes are prohibited once issue #26 installs the branch rules.

### Hotfixes

A production hotfix branches from `FBOProd`, passes the applicable critical checks, and returns through a pull request to `FBOProd`. The released commit receives a patch tag. The equivalent reviewed change is then forward-ported to `FBODev` and to any still-active release branch before the hotfix is considered complete.

### Protection and review with one developer

`FBODev`, active `Release-<version>` branches, and `FBOProd` require pull requests, current applicable checks, resolved conversations, and no force pushes. The solo developer completes a recorded self-review checklist covering scope, tests, migrations, security, artifacts, documentation, and rollback/forward-fix impact.

An independent code approval is requested when a qualified reviewer is available but is not a required merge rule while the project has only one developer. Security-, database-, identity-, fuel-ledger-, CI/platform-, recovery-, and production-sensitive changes require explicit risk evidence and the named stakeholder/release approval before production promotion. When a second qualified contributor joins, issue #26 changes branch rules to require at least one non-author approval and path-owner review.

## Consequences

### Benefits

- Routine work cannot accidentally become the production record merely because `FBOProd` is the default branch.
- `FBODev` gives the sole developer one integration target and preserves the work-in-progress limit.
- The release branch provides a stable acceptance candidate while later development may continue.
- NonProd and production use identical signed candidate artifacts even though the production promotion creates its own Git commit.
- Version tags and release manifests provide an auditable relationship between source, candidate evidence, and production digests.

### Costs and risks

- Pull requests must explicitly select `FBODev`; the GitHub default points to `FBOProd` for production-record reasons.
- Issue closure after `FBODev` merge needs an explicit manual or automated step because GitHub does not apply default-branch closing keywords there.
- Release and hotfix fixes must be forward-ported deliberately, creating a small bookkeeping cost.
- Branches can diverge if feature work is allowed onto the release/production lines or fixes are not forward-ported.
- Solo self-review has less defect-detection independence, so automated checks and production approval carry more weight until another qualified contributor exists.

## Rejected alternatives

| Alternative | Reason not selected |
| --- | --- |
| Merge every ticket directly to `FBOProd` | It makes the production record a development integration branch and weakens the desired release boundary. |
| Permanent `FBODev`, `FBONonProd`, and `FBOProd` branches with routine independent changes | Environment branches drift and encourage repeated merges/cherry-picks without guaranteeing that the tested bytes reach production. A versioned release branch is intentionally temporary and controlled. |
| Rebuild after merging the candidate to `FBOProd` | The new build could resolve different dependencies or base images than the accepted NonProd candidate. |
| Require a non-author approval immediately | One developer cannot satisfy it; a permanently blocked rule would encourage bypasses rather than improve review. |
| Keep a long-lived generic release branch | Versioned branches and tags make candidate scope, support, and closure explicit. |

## Compliance and revision

Issue #26 implements the exact checks, branch rules, pull-request base validation, artifact manifest, and solo-review template. The current workflow is:

```text
<issue>-<slug> -> FBODev -> Release-1.0.0 -> FBOProd + v1.0.0
                       Dev          NonProd              Prod
```

Changing the integration branch, allowing routine work into `FBOProd`, rebuilding between NonProd and Prod, removing forward-port requirements, or changing the review transition trigger requires architecture/release review and an ADR update or superseding decision.
