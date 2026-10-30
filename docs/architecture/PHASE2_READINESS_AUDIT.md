# Phase 2 readiness audit

Audit date: 29 October 2026. The complete GT2.0 workspace was inspected before Phase 2 code changes.

## 1. Existing implementation

### Frontend and Android

- Angular 22.2 standalone application with strict TypeScript 6, Capacitor 8.6, Android package `com.gutta.gt2`, and min/target SDK 24/36.
- `@capacitor-community/sqlite` 8.1.1 opens `gt2_operational` in encrypted `secret` mode. A random 256-bit database secret is generated on-device and stored by the plugin's Android secure-secret facility.
- SQLite schema version 1 contains `local_users`, `patient_number_sequence`, `patients`, `sync_outbox`, and `schema_migrations`.
- Offline authentication is local-only: PBKDF2-HMAC-SHA-256 with per-user salt and 310,000 iterations, memory-only session, route guard, and explicit logout.
- Patient create/update/delete repository paths submit the Patient mutation and outbox insert in one native `executeSet(..., true)` call.
- The approved shell exposes a compact Sync icon immediately beside Logout; Phase 1 only shows a placeholder message.
- Business modules other than Patient are intentionally In Progress and remain out of scope.

### Current event contract

Each outbox row has UUID `event_id`, contract version 1, `PATIENT` entity type, immutable UUID entity ID, operation (`CREATE`, `UPDATE`, `DELETE`), JSON payload, entity version, UTC created time, status, attempt count, error, and acknowledgment time.

Payload shape:

```json
{
  "contractVersion": 1,
  "entityType": "PATIENT",
  "operation": "UPDATE",
  "patient": {
    "id": "uuid",
    "patientNumber": "P-00001",
    "firstName": "...",
    "lastName": null,
    "gender": "OTHER",
    "age": 30,
    "mobile": "0000000000",
    "address": "...",
    "referredBy": "SELF",
    "doctorName": null,
    "consultationFee": 100,
    "registrationDate": "UTC ISO-8601",
    "active": true,
    "createdAt": "UTC ISO-8601",
    "updatedAt": "UTC ISO-8601",
    "deletedAt": null,
    "recordVersion": 2
  }
}
```

Ordering is stable by `(created_at, event_id)` and causal ordering is represented by `entity_version`. Outbox rows survive restart/reboot and are never removed before acknowledgment.

### Existing backend and contracts

`backend/` contains documentation only; there is no server scaffold. Existing Phase 1 documents already define the intended one-way architecture, tenant/device trust boundary, idempotent upload, acknowledgment, snapshots, and logical restore. These decisions are retained and refined rather than replaced.

## 2. Reusable components

- Patient UUID, human Patient number, timestamps, tombstone, and record-version model.
- Versioned full-state Patient event payload.
- Atomic local Patient/outbox transaction.
- Outbox status/attempt/error/acknowledgment columns.
- Encrypted SQLite database for business data and future device credential storage.
- Existing Sync icon, placement, SVG, toast style, and application shell.
- Existing backup API architectural contract and Phase 2 planning documents.
- Java 21 and Maven 3.9.16 available locally.

Spring Boot 4.1.2 is selected because it is the current stable release and officially supports Java 17–27 and Maven 3.6.3+, making the local Java 21/Maven 3.9 environment compatible.

## 3. Missing synchronization infrastructure

- No Spring Boot application, PostgreSQL schema, Flyway migration, health endpoint, or deployment configuration.
- No tenant/device/enrollment/credential lifecycle.
- No authenticated event ingestion, idempotency store, batch history, per-event acknowledgment, ordering enforcement, or server Patient projection.
- No frontend network client, device registration/configuration, pending-event repository, bounded batch, acknowledgment processor, retry/status UI, or overlap prevention.
- No logical snapshot creation/upload, encrypted server snapshot store, authorized catalog/download, or clean-device restore.
- No Phase 2 backend/frontend/integration tests.

## 4. Schema compatibility

No Patient or existing outbox column needs to be rewritten for incremental backup. PostgreSQL stores business-equivalent fields using UUID, `timestamptz`, integer paise, boolean active state, deletion time, and record version; it does not copy SQLite implementation details.

The one required local schema change is additive SQLite version 2:

- `sync_configuration`: one encrypted row containing HTTPS server URL, registered tenant/device IDs, opaque device token, device name, registration time, and last successful sync time.

The device token is unique per installation and returned once during enrollment; no shared production API secret is embedded in the APK. The existing SQLCipher database provides at-rest protection. SQLite v1 remains immutable. The Capacitor upgrade API will register an explicit v1→v2 migration before opening an existing database; fresh installs receive the complete idempotent schema.

Acknowledgment updates use conditional parameterized statements in one local transaction. Only server results `ACCEPTED` or `ALREADY_PROCESSED` become `ACKNOWLEDGED`; rejected/retryable/missing results remain durable with bounded diagnostics.

## 5. Security requirements

- HTTPS only in the Android sync configuration; Android cleartext traffic remains disabled.
- One-use, expiring enrollment codes; random per-device bearer tokens stored only as SHA-256 hashes on the server.
- Tenant/device identity derived exclusively from the authenticated principal, with request `deviceId` treated only as a consistency assertion.
- Separate high-entropy admin bootstrap token supplied by environment, never by the APK or repository.
- Constant-time token comparison where direct secret comparison is required; revocation and last-seen tracking.
- Database uniqueness on `(tenant_id, event_id)`, tenant-scoped Patient number/duplicate constraints, and transactionally atomic Patient mutation plus processed-event marker.
- CORS restricted to configured origins (default `https://localhost` for Capacitor); health is the only unauthenticated operational endpoint besides one-use device registration.
- Snapshot size/type limits, SHA-256 verification, AES-256-GCM at-rest encryption with an environment-supplied master key, randomized file names, normalized-path enforcement, and tenant-authorized retrieval.
- Logs and responses exclude credentials, snapshot bodies, and unnecessary Patient payloads.

## 6. Proposed implementation sequence

1. Scaffold Spring Boot 4.1.2 modular monolith and environment configuration.
2. Create Flyway PostgreSQL schema for tenants, enrollment codes, devices, Patients, batches, events, acknowledgments, snapshots, and recovery audit.
3. Implement admin bootstrap, one-use enrollment, device bearer authentication, revocation, and tenant principal.
4. Implement bounded V1 event ingestion with per-event transactions, request hashes, idempotency, ordering, durable results, and batch replay.
5. Add SQLite v2 configuration migration and targeted Angular Sync service/panel behind the existing icon.
6. Implement logical Patient snapshots, server encryption/storage, authorized catalog/download, and clean-local-database restore.
7. Add backend unit/security tests and real-PostgreSQL Testcontainers tests; add frontend repository/service/status tests.
8. Run local builds and all tests possible in the available environment; prepare isolated RWP deployment artifacts without deploying.

## 7. Architectural conflicts and resolutions

### No local PostgreSQL/container runtime

Java and Maven are installed, but Docker, Podman, and `psql` are absent. The implementation will include Testcontainers PostgreSQL integration tests, but those tests cannot execute locally until a supported container runtime is installed. Unit, MVC/security, frontend, build, schema-static, and packaging tests remain executable. This quality-gate limitation must be reported rather than hidden.

### Snapshot consistency versus raw database copying

Copying an active SQLCipher file is unsafe and would include local credentials. Phase 2 uses a logical snapshot created from a single SQLite read statement over Patient rows and explicit metadata. It excludes local-user password hashes and device tokens. The server verifies the exact-byte checksum and encrypts the stored object. Restore reconstructs Patients and sequence metadata explicitly; PostgreSQL files/exports are never imported into SQLite.

### Recovery overwrite safety

V1 restore is allowed only when the local Patient table is empty. It does not silently overwrite a functioning tablet. A replacement installation must complete local user setup and device enrollment, then select an authorized snapshot. A future operator-approved merge/overwrite workflow is out of scope.

### One-way backup semantics

Incremental event upload never pulls server edits into an operational tablet. Snapshot download exists only for explicit clean-device recovery. Multi-device collaboration, background sync, and conflict merging remain excluded.
