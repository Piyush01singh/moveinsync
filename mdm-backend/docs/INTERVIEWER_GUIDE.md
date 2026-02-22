# Interviewer Quick Guide

This guide helps you validate the project in 10 minutes.

## 1. What to evaluate quickly

- Architecture clarity: layered Spring Boot (`controller -> service -> repository -> model`)
- Governance: role-based admin controls and approval workflow
- Lifecycle rigor: strict state transitions and failure-stage capture
- Auditability: immutable, hash-chained audit logs with verification endpoint
- Operational basics: dashboard, actuator health, backup export/import

## 2. Run the stack

```bash
docker compose up -d
docker run --rm -v ${PWD}:/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 mvn clean test
docker run --rm -p 8080:8080 --name mdm-app --network host -v ${PWD}:/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 java -jar target/mdm-0.0.1-SNAPSHOT.jar
```

If you prefer local Java, run `java -jar target/mdm-0.0.1-SNAPSHOT.jar`.

## 3. Default credentials

- `admin / admin123`
- `release_manager / release123`
- `product_head / product123`
- Device API key: `moveinsync-device-key`

## 4. High-signal API checks

- Health: `GET /actuator/health`
- Dashboard: `GET /api/v1/admin/dashboard?inactiveMinutes=60` (admin/release_manager/product_head)
- Schedule ledger: `GET /api/v1/admin/schedules/ledger`
- Audit timeline: `GET /api/v1/admin/audit/{imei}`
- Audit integrity: `GET /api/v1/admin/audit/verify`
- Backup export/import: `POST /api/v1/admin/backup/export`, `POST /api/v1/admin/backup/import`

## 5. Governance checks

- Release manager can create schedules.
- Product head can approve schedules.
- Downgrade schedules are rejected.
- Invalid lifecycle jumps are rejected with clear 4xx errors.

## 6. Code map for interview discussion

- Security/authz: `src/main/java/com/moveinsync/mdm/config/SecurityConfig.java`
- Bootstrap users: `src/main/java/com/moveinsync/mdm/config/AdminUserBootstrap.java`
- Lifecycle engine: `src/main/java/com/moveinsync/mdm/service/UpdateLifecycleService.java`
- Schedule/admin logic: `src/main/java/com/moveinsync/mdm/service/AdminService.java`
- Device heartbeat + policy: `src/main/java/com/moveinsync/mdm/service/DeviceService.java`
- Audit chain + immutability: `src/main/java/com/moveinsync/mdm/service/AuditLogService.java`, `src/main/java/com/moveinsync/mdm/config/AuditImmutabilityInitializer.java`

## 7. Known intentional scope choices

- Caching is implemented with local Caffeine and explicit eviction endpoint.
- PostgreSQL is the source of truth; Docker is used for reproducible runtime.
