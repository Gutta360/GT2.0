# Historical Phase 1 backup API draft

Status: superseded by the implemented Phase 2 contract in
[`SYNC_API_CONTRACT.md`](SYNC_API_CONTRACT.md). This file is retained only as a
record of the pre-implementation design and must not be used to integrate a
client or operate the service.

## Conventions

- Base path: `/api/v1`
- Transport: HTTPS only; reject cleartext and invalid certificates.
- Media type: `application/json` except snapshot upload/download.
- Time: UTC RFC 3339/ISO-8601.
- IDs: UUID strings.
- Authentication: Spring Security validates a tenant-scoped device credential. Tenant identity is derived from the authenticated principal; a body `tenantId` is correlation/validation data, never sole authorization.
- Every response includes `X-Request-Id`; clients send or receive a generated request ID.
- Bodies and validation errors must never echo credentials or unnecessary patient payloads.

## Device registration

`POST /api/v1/devices/register`

```json
{
  "enrollmentCode": "one-use out-of-band code",
  "devicePublicKey": "base64url public key",
  "applicationId": "com.gutta.gt2",
  "applicationVersion": "phase2-version",
  "platform": "ANDROID"
}
```

Successful response `201`:

```json
{
  "tenantId": "uuid",
  "deviceId": "uuid",
  "credential": "protected bootstrap result",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

The concrete key/credential flow must be finalized by the Phase 2 threat model. Enrollment codes are single-use, short-lived, tenant-bound, and never stored in normal logs. Rotation and revocation endpoints are required before production.

## Incremental upload

`POST /api/v1/backup/events:upload`

Headers include authenticated device credentials and `Idempotency-Key: <batch UUID>`. A repeated batch key must return the original logical result.

```json
{
  "contractVersion": 1,
  "tenantId": "uuid",
  "deviceId": "uuid",
  "batchId": "uuid",
  "events": [
    {
      "eventId": "uuid",
      "entityType": "PATIENT",
      "entityId": "uuid",
      "operation": "UPDATE",
      "entityVersion": 2,
      "createdAt": "2026-10-29T07:31:00Z",
      "payload": {
        "contractVersion": 1,
        "entityType": "PATIENT",
        "operation": "UPDATE",
        "patient": {}
      }
    }
  ]
}
```

Successful/partially accepted response `200`:

```json
{
  "batchId": "uuid",
  "serverReceivedAt": "2026-10-29T07:31:03Z",
  "results": [
    {
      "eventId": "uuid",
      "status": "ACCEPTED",
      "acknowledgedAt": "2026-10-29T07:31:03Z",
      "code": null,
      "message": null
    }
  ]
}
```

Per-event status:

- `ACCEPTED`: durably committed; tablet may mark acknowledged.
- `DUPLICATE`: previously durably committed with identical identity; also safe to acknowledge.
- `RETRY`: transient server condition; keep/requeue locally.
- `REJECTED`: permanent contract/auth/entity error; retain locally for operator resolution.

The whole HTTP request returning 2xx does not imply every event was accepted. The tablet updates each outbox row only from its explicit result.

Server uniqueness is at least `(tenant_id, event_id)`. It stores the request hash so reuse of an event ID with different content is rejected and audited. Entity version checks are scoped to `(tenant_id, entity_type, entity_id)`.

Suggested HTTP failures:

- `400` malformed envelope;
- `401` unauthenticated/expired credential;
- `403` device revoked or tenant mismatch;
- `409` idempotency-key/content conflict;
- `413` batch too large;
- `422` unsupported contract/schema semantics;
- `429` throttled with `Retry-After`;
- `5xx` retryable unless a documented response says otherwise.

## Acknowledgment recovery

If an upload response is lost, resending the same events must return `DUPLICATE` or the original result. Optionally expose:

`POST /api/v1/backup/events:status`

with a bounded list of event IDs, returning their durable status. This is reconciliation, not permission to delete unknown events.

## Recovery snapshot upload

1. `POST /api/v1/backup/snapshots` creates an upload session containing schema version, snapshot contract version, last included event watermark, record counts, byte length, and SHA-256 checksum.
2. The service returns a short-lived authenticated upload target/session.
3. The tablet uploads an encrypted logical snapshot stream.
4. `POST /api/v1/backup/snapshots/{snapshotId}:complete` verifies byte count, checksum, tenant/device, format, and watermark before setting `AVAILABLE`.

Snapshot metadata example:

```json
{
  "snapshotContractVersion": 1,
  "localSchemaVersion": 1,
  "lastEventId": "uuid",
  "lastEventCreatedAt": "2026-10-29T07:31:00Z",
  "recordCounts": { "patients": 123 },
  "sha256": "hex digest",
  "contentLength": 123456
}
```

The snapshot is a logical, versioned export—not a SQLite file intended for PostgreSQL merging.

## Snapshot catalog and restore

- `GET /api/v1/backup/snapshots` lists authorized available snapshots and watermarks.
- `POST /api/v1/restore/requests` creates an audited, short-lived restore authorization for a selected snapshot/device.
- `GET /api/v1/restore/requests/{restoreId}/snapshot` streams the authenticated encrypted logical snapshot.
- `GET /api/v1/restore/requests/{restoreId}/events?after=<watermark>` returns accepted events after the snapshot watermark in stable order.
- `POST /api/v1/restore/requests/{restoreId}:complete` records checksum/result and closes the authorization.

The tablet restores into a separate encrypted database, validates all metadata/counts/checksums, replays post-watermark events, and swaps databases only after verification. Existing local data is not overwritten without an explicit operator flow and recoverable pre-restore backup.

## Server persistence and audit

PostgreSQL tables should separate device registration, received events, per-entity projection/current backup state, batches, acknowledgments, snapshots, and restore audit. All tenant-owned primary/unique keys include tenant scope. Flyway owns schema changes.

Audit records cover enrollment, credential rotation/revocation, uploads, rejections, snapshot completion/deletion, restore authorization, and restore completion. Audit/log data must minimize patient content and never contain credentials.

## Limits to finalize in Phase 2

Batch event count/bytes, retry ceilings, credential lifetimes, snapshot frequency/size, acknowledged-event retention, server retention, recovery point/time objectives, cryptographic envelope format, regional data requirements, and operator roles require product/security approval before implementation.
