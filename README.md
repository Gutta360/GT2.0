# GT2.0 — GuttaTemplate2.0

GT2.0 is an offline-first Android dental-clinic template. The approved Phase 1 Angular/Capacitor UI and encrypted SQLite patient workflows remain the operational application. Phase 2 adds a separate Spring Boot/PostgreSQL service for authenticated incremental backup, encrypted logical snapshots, and controlled clean-device recovery.

Phase 2 is implemented but not yet fully acceptance-verified: local unit/build gates and the isolated PostgreSQL 18.6 preflight pass, while physical-tablet recovery still requires execution. Nothing has been deployed to the RWP server.

## Stack

- Angular 22.2, TypeScript 6.0, RxJS 7.8, Capacitor 8.6
- Android package `com.gutta.gt2`; encrypted `@capacitor-community/sqlite` 8.1
- Java 21, Spring Boot 4.1.2, Spring Security, JPA, Flyway
- PostgreSQL, AES-256-GCM snapshot files, Testcontainers 2.0.5

## Repository

```text
frontend/     Angular application, SQLite v2, sync/snapshot client, Capacitor Android
backend/      Spring Boot API, Flyway migration, tests, RWP deployment templates
docs/         Architecture, schemas, API contract, recovery, test plan/results
```

## Frontend build and test

```bash
cd frontend
npm ci
npm test
npm run build
npx cap sync android
cd android
./gradlew assembleDebug
```

The APK is `frontend/android/app/build/outputs/apk/debug/app-debug.apk`. Install with:

```bash
adb install -r frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

Installing with `-r` preserves the approved Phase 1 application data. Do not uninstall or clear data when testing the v1-to-v2 migration. Make a device backup first.

## Backend build and local run

See [backend/README.md](backend/README.md) for required environment variables, secure local provisioning, PostgreSQL setup, tests, and packaging.

```bash
cd backend
mvn test
mvn clean package
```

The backend requires PostgreSQL and exact secrets from the environment. The tablet works offline when it is absent. Its one-use enrollment code and per-device bearer token model avoids any shared secret in the APK.

## Implemented Phase 2 behavior

- Additive SQLite v2 migration preserving local users, patients, numbering, and outbox.
- Foreground/manual one-way batches with explicit acknowledgments, bounded selection, safe retries, and no overlap.
- Tenant/device registration and revocation; environment-only provisioning administration.
- Per-event atomic processing, UUID idempotency, ordered entity versions, tombstones, and partial-batch results.
- Exact-byte SHA-256 logical snapshots encrypted server-side; tenant-scoped list/download and recovery audit.
- Clean-device restore only when local patients are empty; credentials/tokens are never restored.
- Audited, isolated systemd/Tailscale Serve RWP deployment preparation with guarded scripts. Existing Nginx remains unchanged; no deployment was performed.

## Verification status

On 2026-10-29, Angular production build and 15 frontend tests passed; Maven compiled/packaged the service and 2 backend unit tests passed; Capacitor sync and the Android debug APK build passed. The three PostgreSQL Testcontainers tests skip on this Mac, but the approved isolated RWP rerun executed them against a fresh PostgreSQL 18.6 container with zero skips. After correcting five undeployed V1 hash columns to `VARCHAR(64)`, Flyway V1, Hibernate schema validation, and all 5 combined tests passed. No GT2 deployment occurred. Physical clean-device recovery remains mandatory.

Start with the [readiness audit](docs/architecture/PHASE2_READINESS_AUDIT.md), [sync contract](docs/api-contracts/SYNC_API_CONTRACT.md), [backup/recovery runbook](docs/database/BACKUP_AND_RECOVERY.md), and [test results](docs/testing/PHASE2_TEST_RESULTS.md).
