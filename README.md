MoveInSync Mobile Device Management (MDM) Backend
Spring Boot 3 backend for centralized device registry, version governance, controlled rollouts, lifecycle enforcement, and full auditability for the MoveInSync mobile app.

Architecture Choice
This implementation intentionally uses:

Spring Boot
PostgreSQL
Docker (for PostgreSQL runtime)
What Is Implemented
Centralized device heartbeat ingestion with direct DB upsert.
Version repository with immutable publishing.
Compatibility matrix with mandatory intermediate enforcement.
Controlled rollout scheduling by:
fromVersion -> toVersion
region
customization tag
device group
scheduled/immediate mode
rollout percentage
Strict downgrade prevention:
at schedule creation
at heartbeat policy decision
at installation-completion validation
Update lifecycle state machine enforcement:
UPDATE_SCHEDULED -> DEVICE_NOTIFIED -> DOWNLOAD_STARTED -> DOWNLOAD_COMPLETED -> INSTALLATION_STARTED -> INSTALLATION_COMPLETED
FAILED with retry-aware transitions
Complete audit trail with structured fields:
actor id, event source, schedule id, from/to version
failure stage and reason
timestamped timeline per IMEI
OS restriction enforcement using supportedOsRange (for example Android 12+, Android 12-14, iOS 16+).
Dashboard completeness metrics:
active/inactive totals
success/failure/pending counts
success and failure rates
rollout progress %
version, region, and device-group distributions
failure stage distribution
region x version heatmap
Security:
HTTP Basic for admin APIs (ROLE_ADMIN)
API key (X-API-KEY) for device/update APIs
Caching:
local Caffeine cache for app-version lookups
explicit cache eviction endpoint
Monitoring:
Actuator health/metrics/prometheus
Micrometer counters for lifecycle/policy events
Tech Stack
Java 21
Spring Boot 3.4
Spring Web, Spring Data JPA, Spring Validation
Spring Security (HTTP Basic + API Key filter)
Spring Cache + Caffeine (in-memory)
PostgreSQL
Spring Boot Actuator + Micrometer
JUnit 5 + Mockito
Run Locally
Prerequisites:

Java 21 available on PATH
Maven 3.9+ available on PATH
Docker Desktop running
1. Start infrastructure
docker compose up -d
Starts PostgreSQL only.

2. Build and test
mvn clean test
3. Run app
java -jar target/mdm-0.0.1-SNAPSHOT.jar
4. Verify health
curl http://localhost:8080/actuator/health
5. Open web dashboard
http://localhost:8080/
Core APIs
Device APIs (require X-API-KEY)
POST /api/v1/device/heartbeat
POST /api/v1/update/status
Admin APIs (require HTTP Basic)
POST /api/v1/admin/versions
POST /api/v1/admin/compatibility
POST /api/v1/admin/schedules
POST /api/v1/admin/schedules/{id}/approve
GET /api/v1/admin/schedules
GET /api/v1/admin/schedules/ledger
GET /api/v1/admin/dashboard?inactiveMinutes=60
GET /api/v1/admin/devices/count?region=Bangalore&version=4.2
GET /api/v1/admin/audit/{imeiNumber}
POST /api/v1/admin/cache/app-versions/evict
Security Configuration
Set these environment variables (recommended):

MDM_ADMIN_USERNAME
MDM_ADMIN_PASSWORD
MDM_DEVICE_API_KEY
MDM_MAX_LIFECYCLE_RETRIES
MDM_APP_VERSION_CACHE_TTL_MINUTES
MDM_APP_VERSION_CACHE_MAX_SIZE
Defaults are defined in src/main/resources/application.yml for local development.

Evaluation Criteria Mapping
Authentication: SecurityConfig, DeviceApiKeyFilter
Time/space and trade-offs: docs/DESIGN.md
Failure handling: lifecycle retry controls + transactional writes + structured errors
OOPS: layered controller/service/repository/model design
Monitoring: actuator + micrometer lifecycle/policy counters
Caching: CacheConfig, AppVersionService, cache eviction endpoint
Error handling: GlobalExceptionHandler, ApiException, validation responses
Governance: approval workflow + immutable versioning + strict downgrade blocks
Testing
Unit tests:

src/test/java/com/moveinsync/mdm/service/AdminServiceTest.java
src/test/java/com/moveinsync/mdm/service/DeviceServiceTest.java
src/test/java/com/moveinsync/mdm/service/UpdateLifecycleServiceTest.java
