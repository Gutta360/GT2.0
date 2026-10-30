# Phase 2 test results

Date: 2026-10-29 (Asia/Kolkata)

## Executed and passed

- `npm test`: 7 files, 15 tests passed. Covers existing auth/database/patient validation plus sync selection, acknowledgments, failed retry, network interruption, overlap prevention, snapshot pending-event safety, and refusal to overwrite local patient data.
- `npm run build`: Angular production bundle passed; 424.15 kB initial raw / 106.05 kB estimated transfer.
- `mvn -Dmaven.repo.local=/private/tmp/gt2-m2 test`: build passed; 2 unit tests passed.
- `mvn -Dmaven.repo.local=/private/tmp/gt2-m2 package`: executable Spring Boot JAR built successfully (60,051,952 bytes; SHA-256 `abde9b2203b93f9d3fca2c53e797655f0eca579711a9e6aaa992936770358aeb`).
- `npx cap sync android`: passed and detected `@capacitor-community/sqlite` 8.1.1.
- `./gradlew assembleDebug`: passed; final APK built at `frontend/android/app/build/outputs/apk/debug/app-debug.apk` (14,517,252 bytes). AAPT reports application ID `com.gutta.gt2`, min SDK 24, target SDK 36; the APK contains SQLCipher native libraries for four ABIs and the encryption-enabled Capacitor configuration.
- Packaged-JAR smoke start: configuration binding, repository discovery, embedded Tomcat initialization, security-filter registration, datasource creation, and Flyway initialization were reached. Startup then stopped at the expected `127.0.0.1:5432 connection refused` boundary because no PostgreSQL server is installed/running.
- TypeScript strict typecheck passed as part of the Angular build (and separately with `tsc --noEmit`).

## Server-isolated integration validation

- Locally, three real-PostgreSQL/Testcontainers integration tests compile but skip because Docker, Podman, and PostgreSQL are unavailable. The approved isolated RWP Phase A rerun executed all three against a fresh disposable PostgreSQL 18.6 container with zero skips. After correcting the five undeployed V1 hash columns to `VARCHAR(64)`, Flyway V1 applied successfully, Hibernate schema validation passed, and all three integration methods completed their assertions. The combined server run passed 5/5 tests with zero failures, errors, or skips. See [`RWP_PHASE_A_PREFLIGHT_RESULTS.md`](RWP_PHASE_A_PREFLIGHT_RESULTS.md).

## Implemented but not yet device-verified

- The updated Phase 2 APK and physical-tablet upgrade/recovery workflow have not yet been exercised. These are mandatory before Phase 2 completion.
- RWP systemd/Tailscale Serve configuration is prepared from a read-only server audit but was not deployed, as required. Existing Nginx remains unchanged.

## Quality-gate status

| Gate | Status | Evidence needed to close |
|---|---|---|
| G1 Backend Ready | Preflight passed | 5/5 tests; Flyway V1 and Hibernate validation passed on isolated PostgreSQL 18.6 |
| G2 Security Ready | Integration verified | Unit and PostgreSQL registration/authentication tests passed; production-secret setup remains deployment-time work |
| G3 Incremental Backup | Partially verified | Client/unit pass; real DB/API/device E2E pending |
| G4 Sync UI | Implemented, device-unverified | Install updated APK and test panel |
| G5 Failure Recovery | Partially verified | Unit failure/retry pass; full E2E pending |
| G6 Snapshot | Implemented, unverified | Real DB encrypted upload/download and tablet test |
| G7 Restore | Implemented, unverified | Clean physical/test installation recovery drill |
| G8 Deployment Ready | Documented, not deployed | Isolated Ubuntu rehearsal before RWP authorization |

Phase 2 must not be declared complete until the outstanding mandatory gates pass.
