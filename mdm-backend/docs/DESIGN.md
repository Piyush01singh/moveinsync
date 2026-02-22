# MDM Backend Design, Complexity, and Trade-offs

## 1. High-Level Design

Layered backend:

- Controller layer: request/response and validation boundaries
- Service layer: policy decisions, lifecycle state machine, audit orchestration
- Repository layer: persistence and aggregation queries

Core entities:

- `Device`
- `AppVersion`
- `VersionCompatibility`
- `UpdateSchedule`
- `DeviceUpdateState`
- `AuditLog`

## 2. Authentication and Authorization

- Admin endpoints: HTTP Basic + `ROLE_ADMIN`
- Governance roles:
  - `ROLE_RELEASE_MANAGER`: create versions, compatibility, schedules
  - `ROLE_PRODUCT_HEAD`: approve mandatory schedules
  - `ROLE_ADMIN`: full access
- Device/update endpoints: API key (`X-API-KEY`)
- `/actuator/health` is public
- Other actuator/admin endpoints require admin auth

Trade-off:

- API key for devices is simpler operationally than OAuth/JWT and sufficient for this MDM scope.

## 3. Time and Space Complexity

### A. Heartbeat (`POST /api/v1/device/heartbeat`)

- Device registry upsert: O(1) average by IMEI primary key
- Schedule lookup: O(k) over matched schedules (indexed filters)
- Compatibility lookup: O(1) average by `(from_version,to_version)` unique lookup
- OS compatibility check: O(1)
- Space: O(1) response payload

### B. Schedule creation (`POST /api/v1/admin/schedules`)

- Version lookups: O(1) each
- Compatibility check: O(1)
- Save schedule: O(1)
- Space: O(1)

### C. Dashboard (`GET /api/v1/admin/dashboard`)

- Distribution aggregations: O(n) over grouped scans
- Heatmap aggregation: O(n) grouped by `(region,version)`
- Rollout progress estimate: O(s) schedule count + scope count queries
- Space: O(v + r + g + h), where:
  - `v`: versions
  - `r`: regions
  - `g`: device groups
  - `h`: heatmap cells

## 4. Failure Handling and Recovery

Implemented mechanisms:

- Transactional service writes for state consistency
- Lifecycle retry limits (`mdm.lifecycle.max-retries`)
- Strict transition rejection for invalid workflow steps
- Structured API errors via `ApiException` + global handler
- Persistent audit timeline for root-cause analysis
- Docker volume persistence for PostgreSQL
- In-app snapshot backup/export and transactional restore/import endpoints
- PostgreSQL trigger-level audit immutability enforcement (`UPDATE/DELETE` blocked on `audit_logs`)

Recommended production backup strategy:

- periodic `pg_dump`
- point-in-time recovery (WAL archiving)

## 5. Governance and Rollout Safety

- Downgrades blocked:
  - schedule creation stage
  - heartbeat decision stage
  - installation completion stage
- Compatibility matrix enforced for mandatory intermediates
- Mandatory target versions require explicit approval (`PENDING -> APPROVED`)
- Rollout percentage supports staged deployments
- Device-group targeting supports controlled cohorts

## 6. Caching Strategy

- Caffeine local cache (`appVersions`)
- TTL + max-size eviction
- Explicit admin eviction endpoint

Trade-off:

- Local cache is simpler than distributed cache and sufficient for single-node or small-cluster demos.

## 7. Monitoring and Observability

- Actuator: `health`, `info`, `metrics`, `prometheus`
- Micrometer counters:
  - `mdm.lifecycle.transition{status,source}`
  - `mdm.lifecycle.blocked{type}`
  - `mdm.policy.blocked{type}`
- Structured audit and service logs for operational tracing

## 8. Lifecycle State Machine

Enforced progression:

1. `UPDATE_SCHEDULED`
2. `DEVICE_NOTIFIED`
3. `DOWNLOAD_STARTED`
4. `DOWNLOAD_COMPLETED`
5. `INSTALLATION_STARTED`
6. `INSTALLATION_COMPLETED`
7. `FAILED` (allowed from in-flight states, with retry policy)

Failure handling:

- `FAILED` requires `failureStage` + `failureReason`
- Retry transitions depend on failure stage
- After max retries, only `UPDATE_SCHEDULED` reset is allowed

## 9. Auditability

Each audit event stores:

- actor id
- event source
- device IMEI
- schedule id
- lifecycle/admin action
- from/to version
- failure stage/reason (if failed)
- details
- timestamp
- previous hash + event hash (SHA-256 hash-chain)

This supports end-to-end timeline reconstruction per device.

## 10. Key Trade-offs Chosen

1. Synchronous DB writes vs event broker:
   - simpler operations, easier explainability, reduced infra complexity
2. Local cache vs distributed cache:
   - lower operational overhead, acceptable for current scope
3. Strict transition enforcement vs flexibility:
   - stronger governance/compliance, more explicit device behavior requirements
