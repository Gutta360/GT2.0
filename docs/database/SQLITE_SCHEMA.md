# SQLite schema version 1

The authoritative schema source is `frontend/src/app/core/database/migrations.ts`. The native database name is `gt2_operational`; Android opens it encrypted in `secret` mode.

## `schema_migrations`

| Column | Type | Rules |
|---|---|---|
| `version` | INTEGER | Primary key |
| `applied_at` | TEXT | Required UTC ISO-8601 instant |

Records the locally applied logical schema versions. Version 1 is inserted idempotently after the bootstrap DDL succeeds.

## `local_users`

| Column | Type | Rules |
|---|---|---|
| `id` | TEXT | UUID primary key |
| `username` | TEXT | Required, case-insensitive unique |
| `display_name` | TEXT | Required |
| `password_salt` | TEXT | Required Base64 salt |
| `password_hash` | TEXT | Required Base64 PBKDF2 result |
| `password_iterations` | INTEGER | Required, at least 100,000; current value 310,000 |
| `enabled` | INTEGER | Boolean 0/1, default 1 |
| `created_at`, `updated_at` | TEXT | Required UTC instants |
| `last_login_at` | TEXT | Nullable UTC instant |

No plaintext password or recoverable password value is stored.

## `patient_number_sequence`

| Column | Type | Rules |
|---|---|---|
| `singleton` | INTEGER | Primary key constrained to 1 |
| `next_number` | INTEGER | Positive next value |

The singleton starts at 1. A create batch conditionally advances it and emits `P-` plus the zero-padded observed value. The UUID—not this display number—is the future synchronization identity.

## `patients`

| Column | Type | Rules |
|---|---|---|
| `id` | TEXT | Immutable UUID primary key |
| `patient_number` | TEXT | Required unique human ID |
| `first_name` | TEXT | Trimmed length 2–50 |
| `last_name` | TEXT | Nullable, max length 50 |
| `gender` | TEXT | `MALE`, `FEMALE`, `OTHER` |
| `age` | INTEGER | 1–120 |
| `mobile` | TEXT | Exactly 10 digits |
| `address` | TEXT | Trimmed length 2–255 |
| `referred_by` | TEXT | `DOCTOR`, `PATIENT`, `ONLINE`, `SELF`, `OTHER` |
| `doctor_name` | TEXT | Required length 2–100 when referral is `DOCTOR` |
| `consultation_fee_cents` | INTEGER | Non-negative paise |
| `registration_date` | TEXT | Required UTC instant |
| `is_active` | INTEGER | Boolean 0/1, default 1 |
| `created_at`, `updated_at` | TEXT | Required UTC instants |
| `deleted_at` | TEXT | Nullable tombstone instant |
| `record_version` | INTEGER | Positive optimistic/entity version |

Indexes:

- unique `patient_number`;
- unique `lower(trim(first_name)), mobile` for reference duplicate semantics;
- `(is_active, first_name, last_name, patient_number)` for listing/search ordering;
- `mobile` for lookup.

SQLite cannot encode every application-level pattern cleanly in portable DDL, so service/form validation remains mandatory. Constraints provide a second boundary for core enum, range, mobile, referral, active, fee, and version rules.

## `sync_outbox`

| Column | Type | Rules |
|---|---|---|
| `event_id` | TEXT | UUID primary key/idempotency key |
| `contract_version` | INTEGER | Required, default 1 |
| `entity_type` | TEXT | Required; currently `PATIENT` |
| `entity_id` | TEXT | Required entity UUID |
| `operation` | TEXT | `CREATE`, `UPDATE`, `DELETE` |
| `payload` | TEXT | Required JSON event body |
| `entity_version` | INTEGER | Required Patient record version |
| `created_at` | TEXT | Required UTC instant/order component |
| `sync_status` | TEXT | `PENDING`, `IN_FLIGHT`, `ACKNOWLEDGED`, `FAILED` |
| `attempt_count` | INTEGER | Default 0 |
| `last_error` | TEXT | Nullable transport/server diagnostic |
| `acknowledged_at` | TEXT | Nullable durable acknowledgment time |

Pending work is indexed by `(sync_status, created_at, event_id)` for stable batch selection.

## Storage and backup behavior

The database is encrypted at rest by the native plugin. Android cloud backup and device transfer exclude database, shared-preference, root, and external app domains. This prevents uncontrolled platform backup but also means Phase 1 has no disaster recovery if application data or the device is lost. Phase 2 supplies explicit authenticated backup and restore.
