# Sync outbox design

## Purpose

The outbox durably records the intent to back up every Patient change. It exists in Phase 1 even though no transport exists, preventing a future sync feature from having to infer historical changes from current rows.

## Atomicity

Patient writes and their event are submitted with `SQLiteDBConnection.executeSet(statements, true)`. This executes the whole set in one native transaction and one Capacitor bridge invocation.

- Create: conditionally advance Patient sequence → conditionally insert Patient → conditionally insert CREATE event.
- Update: optimistic-version Patient update → insert UPDATE event only when the update changed one row.
- Delete: optimistic soft-delete/tombstone update → insert DELETE event only when the update changed one row.

Any constraint/plugin error rolls the native batch back. A failed duplicate create was verified to leave Patient/outbox counts unchanged.

## Event contract version 1

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

The example is illustrative and contains no real person. The outbox envelope separately stores event/entity IDs, operation, contract/entity versions, and created time so a sender can batch without parsing payload JSON.

DELETE contains the final tombstoned Patient representation (`active=false`, `deletedAt` set), which lets the server retain audit/history and prevents accidental resurrection.

## Ordering and idempotency

Event UUID is the server idempotency key. Selection order is `(created_at, event_id)`, and `entity_version` enforces per-Patient order. Equal wall-clock timestamps are resolved by event ID for stable traversal; they do not imply causal order between unrelated Patients.

Phase 2 must accept duplicate uploads without duplicating effects, reject/gap-buffer invalid entity versions according to a documented rule, and acknowledge every event individually.

## State machine

```text
PENDING -> IN_FLIGHT -> ACKNOWLEDGED
    ^          |
    |          v
    +------- FAILED
```

`FAILED` is a visible retry/error state, not deletion. Retry increments `attempt_count` and records a bounded, non-sensitive `last_error`. A process interruption may safely return stale in-flight events to pending because server ingestion is idempotent.

## Retention

Never delete an event before durable server acknowledgment. Phase 2 may prune acknowledged events only after a retention window and confirmation that a recovery snapshot covers their versions. Pending/failed events are never time-pruned. Monitoring must alert on age, count, repeated attempts, and storage growth.

## Privacy

Payloads contain patient data and therefore remain inside the encrypted database. Future logs must use event/entity identifiers and status, not payload bodies. Upload requires TLS, authenticated tenant/device context, server-side encryption/backup controls, and audited access.
