# FBO Manager analytics

Analytics and data-warehouse workloads are intentionally outside the MVP. PostgreSQL current-state views support bounded operational screens; they are not an analytics store and must not be repurposed into unbounded reporting queries on the transactional application path.

Any future analytics capability needs a separate architecture decision covering data ownership, extraction, privacy/retention, workload isolation, and reconciliation with the PostgreSQL source of truth. See the [MVP architecture constraints and non-goals](../docs/architecture.md#14-constraints-and-non-goals).
