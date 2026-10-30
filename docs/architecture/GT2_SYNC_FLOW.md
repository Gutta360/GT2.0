# GT2.0 synchronization flow

## Incremental path

1. Every local patient CREATE, UPDATE, or DELETE and its full state payload are committed atomically with a UUID outbox event.
2. Manual Sync or the browser `online` event starts one foreground sync. A single-flight guard prevents overlap.
3. The client resets abandoned `IN_FLIGHT` rows to `FAILED`, selects `PENDING`/`FAILED` events in `(created_at,event_id)` order, and claims at most 100.
4. The API authenticates the device token and derives the tenant. The request device must equal the authenticated device.
5. The server records a UUID batch, then processes each event in a separate transaction.
6. CREATE must be version 1 and absent. UPDATE/DELETE must be exactly the current server version plus one. DELETE must carry a tombstone (`active=false`, `deletedAt`).
7. The patient mutation and processed marker commit together. Duplicate event content returns `ALREADY_PROCESSED`; reused IDs with changed content and stale/gapped versions are rejected.
8. Only `ACCEPTED` and `ALREADY_PROCESSED` cause local `ACKNOWLEDGED`. All missing, rejected, or retryable results remain `FAILED` with a diagnostic.

Network loss never blocks patient work and never deletes local records. A new retry batch can safely contain the same event IDs.

## Snapshot path

The client first requires an empty pending outbox, then reads all patient rows with one ordered SQLite query, serializes contract version 1/schema version 2 JSON, computes SHA-256 over the exact UTF-8 text, and uploads both. The server validates schema/content, encrypts bytes at rest, stores metadata, and retains the configured newest snapshots.

Restore is intentionally narrow: after device registration, the user chooses a snapshot; the server builds a recovery package by overlaying that baseline with every newer accepted patient version in its current tenant projection; the client downloads and verifies the generated checksum. Recovery is refused unless the local `patients` table is empty; all patients and the next patient-number sequence are inserted in one SQLite batch. Restore creates no outbox events because every included version is already backed up. Success or failure is audited on the server.
