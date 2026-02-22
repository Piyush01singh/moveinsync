# 🚀 MoveInSync – Mobile Device Management (MDM) Backend

Enterprise-grade Spring Boot backend for centralized device registry, version governance, controlled rollouts, lifecycle enforcement, and full auditability for a mobile application.

---

## 🛠 Tech Stack

- **Java 21**
- **Spring Boot 3.4**
- **PostgreSQL**
- **Spring Security** (HTTP Basic + API Key)
- **Spring Data JPA**
- **Caffeine Cache**
- **Spring Boot Actuator + Micrometer**
- **Docker**

---

## ✨ Core Features

- 📱 Device heartbeat ingestion (IMEI-based tracking)
- 🔒 Immutable app version publishing
- 🧩 Compatibility matrix with mandatory intermediate enforcement
- 🎯 Controlled rollouts (region, device group, %, scheduled/immediate)
- ⛔ Strict downgrade prevention (schedule + runtime validation)
- 🔄 Update lifecycle state machine enforcement
- 📊 Dashboard metrics (success/failure rates, rollout %, distributions)
- 📝 Complete audit trail per device
- 📈 Production-ready monitoring & health checks

---

## 🔄 Update Lifecycle

```text
UPDATE_SCHEDULED
→ DEVICE_NOTIFIED
→ DOWNLOAD_STARTED
→ DOWNLOAD_COMPLETED
→ INSTALLATION_STARTED
→ INSTALLATION_COMPLETED
```

✔ Retry-aware failure handling  
✔ Strict transition validation  

---

## 🔐 Security

### Admin APIs
- HTTP Basic Authentication
- ROLE_ADMIN required

### Device APIs
- API Key authentication  
- Header: `X-API-KEY`

---

## ▶ Run Locally

### 1️⃣ Start PostgreSQL
```bash
docker compose up -d
```

### 2️⃣ Build & Test
```bash
mvn clean test
```

### 3️⃣ Run Application
```bash
java -jar target/mdm-0.0.1-SNAPSHOT.jar
```

Health Check:
```
http://localhost:8080/actuator/health
```

---

## 📡 Core APIs

### Device APIs
```
POST /api/v1/device/heartbeat
POST /api/v1/update/status
```

### Admin APIs
```
POST /api/v1/admin/versions
POST /api/v1/admin/schedules
POST /api/v1/admin/schedules/{id}/approve
GET  /api/v1/admin/dashboard
GET  /api/v1/admin/audit/{imei}
```

---

## 🏗 Design Highlights

- Layered architecture (Controller → Service → Repository)
- Transactional lifecycle enforcement
- Immutable version governance
- Structured audit logging
- Observability-first design
- Retry-aware failure management
