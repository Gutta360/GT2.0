# Phase 2 test plan

## Automated gates

- Frontend unit: v2 migration compatibility, ordered pending selection, claim/ack/failure transitions, batching, network interruption, overlap prevention, and existing auth/patient regression.
- Backend unit: token generation/hashing and partial-batch orchestration/counting.
- PostgreSQL integration (Testcontainers): Flyway startup, security, one-use registration, accepted and duplicate events, version gaps, tenant-isolated snapshot access, encryption/checksum round trip.
- Build: Angular production bundle, Capacitor sync, debug APK, Maven test/package.

## Manual local integration

Run PostgreSQL 17 and the backend, provision a tenant/code, register the tablet, then execute the exact offline/edit/sync/repeat/backend-down/retry sequence in the master prompt. Inspect PostgreSQL after each server step. Create a snapshot and restore it only into a clean test installation.

## Physical Android acceptance

Verify upgrade from the approved Phase 1 APK without clearing app data. Confirm local login and patients remain, the schema reaches v2, all patient workflows remain offline, the Sync panel fits the 11-inch display, HTTPS registration works, server downtime does not block CRUD, explicit retry succeeds, and a clean secondary/test installation restores the latest state.

## Deployment rehearsal

In an isolated Ubuntu VM: install PostgreSQL, configure `/etc/gt2/backend.env`, start systemd, verify Flyway/health, place a trusted TLS proxy in front, provision a test tenant, run the API integration sequence, restart services, and run backup/restore drills. For the audited RWP private test, use Tailscale Serve and leave existing Nginx unchanged. Do not point a rehearsal at the existing GDC database.
