# Phase 2 plan

## Objective

Phase 2 adds secure backup, acknowledgment, and recovery without making the tablet dependent on network availability. It is not a direct database-file merge and is not real-time bidirectional multi-device synchronization.

V1 assumes one primary tablet per tenant/client.

## Proposed server

- Java 21 and a supported Spring Boot release.
- Spring Security with short-lived access tokens and device-bound refresh credentials or mutually authenticated device credentials.
- PostgreSQL with explicit `tenant_id` on every tenant-owned row and database-enforced uniqueness scoped to the tenant.
- Flyway-only schema changes.
- Idempotency tables keyed by `(tenant_id, event_id)`.
- Append-only ingestion/audit history, acknowledgment state, snapshot catalog, and restore audit.
- Independent encrypted PostgreSQL backups with tested point-in-time recovery.

The specific current Spring Boot/Security/PostgreSQL versions must be selected at Phase 2 implementation time, then threat-modeled and pinned; Phase 1 intentionally does not generate stale server code.

## Identity and isolation

1. An operator provisions the tenant and a one-use device enrollment code out of band.
2. The tablet generates a device key pair in Android-backed secure storage where available.
3. Registration exchanges the one-use code and public key for a device identifier and renewable credential.
4. Every request is authenticated and derives tenant identity from the credential, never from a trusted request-body field alone.
5. Authorization, repository filters, database constraints, logs, metrics, and backup paths all preserve tenant isolation.
6. Rotation, revocation, stolen-device handling, clock skew, and enrollment-code expiry are tested before production.

## Incremental event flow

1. Read ordered `PENDING`/retryable events by `(created_at, event_id)`.
2. Mark a bounded batch `IN_FLIGHT` without deleting it.
3. Upload over TLS using the versioned contract.
4. Server authenticates device, validates contract and entity version, and inserts each unseen event idempotently.
5. Server returns an explicit per-event result.
6. Tablet marks only durably accepted/duplicate events `ACKNOWLEDGED` with time; retryable failures return to pending with attempt/error metadata.
7. Permanent rejection is visible to an operator and must not be silently dropped.

Retries use bounded exponential backoff with jitter. The server treats the event UUID as the idempotency key. Ordering is monotonic per entity through `entity_version`; contract V1 does not promise wall-clock total ordering across entities.

## Cleanup and growth control

Acknowledged events are retained locally for an operational safety window. Cleanup may delete only events that have a durable server acknowledgment and are older than the configured retention threshold, and only after a recovery snapshot covering their entity versions is confirmed. Pending, in-flight, failed, or unacknowledged events are never age-deleted.

## Snapshot versus incremental backup

Incremental events efficiently preserve change history but are not by themselves a convenient full restore. A recovery snapshot is a separately versioned, integrity-checked representation of the current tenant state at a declared event watermark.

- **Event upload:** frequent, small, append-only, idempotent changes.
- **Recovery snapshot:** periodic complete logical state plus schema/contract version and last included event watermark.
- **Restore:** downloads a selected verified snapshot, validates tenant/device authorization and checksums, restores into a new encrypted local database, then replays accepted events after the watermark.

SQLite database files are never merged into PostgreSQL. Snapshot creation/restoration must use logical records and a staged, rollback-safe database replacement.

## Delivery sequence

1. Threat model, tenant/device provisioning, secrets, audit, and retention decisions.
2. Spring service skeleton, Security, PostgreSQL, Flyway, and tenant-isolation tests.
3. Device registration and credential rotation/revocation.
4. Event ingestion/acknowledgment contract with idempotency and version tests.
5. Android outbox worker with network/battery constraints and observable retry state.
6. Server history/monitoring and safe local acknowledgment cleanup.
7. Snapshot creation, catalog, download, staged restore, and destructive-action confirmations.
8. Independent backups, restore drills, lost-device exercise, and tenant-isolation penetration tests.

## Explicitly out of scope for V1

- real-time or bidirectional multi-device synchronization;
- concurrent edits across multiple tablets;
- direct server access to the tablet SQLite file;
- background claims of success without an acknowledgment;
- automatic destructive restore without operator selection and confirmation.
