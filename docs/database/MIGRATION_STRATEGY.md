# SQLite migration strategy

## Current bootstrap

`DATABASE_VERSION` is 1. On every native initialization, GT2.0:

1. obtains/creates the database encryption secret;
2. creates the encrypted read/write connection at version 1;
3. opens the connection;
4. executes the idempotent version-1 DDL without wrapping it in a nested plugin transaction;
5. inserts schema-migration row 1 with `INSERT OR IGNORE`;
6. marks the database ready only after all steps resolve.

The DDL uses `CREATE TABLE/INDEX IF NOT EXISTS` and an idempotent sequence seed. This is sufficient for a new Phase 1 schema and reinstall/upgrade launches that retain application data.

## Adding version 2 and later

A future version must not edit `SCHEMA_V1` in a way that changes the meaning of an already-applied database. Add an ordered immutable migration and increase `DATABASE_VERSION` only in the same reviewed change.

Each migration must:

- declare `fromVersion`, `toVersion`, and immutable SQL;
- be safe for the SQLite version shipped on the minimum supported Android API;
- execute as one native transaction call where writes are involved;
- insert its `schema_migrations` row only as part of successful completion;
- preserve UUIDs, Patient numbers, tombstones, record versions, and pending outbox rows;
- avoid destructive table replacement unless it uses create/copy/verify/swap inside a rollback-safe plan;
- have fixture tests covering an actual prior-version database, not only a fresh install.

Before a production upgrade, test all supported upgrade paths, database-open failure behavior, interrupted installation, insufficient storage, and rollback/recovery. Never lower the database version or silently recreate a database on migration failure.

## Contract evolution

Database schema version and outbox `contract_version` are independent. A schema migration may preserve event contract V1; an event-contract change may not require a local table change. Readers must reject unsupported future contracts explicitly rather than guessing.

Phase 2 snapshots include both versions and a last-event watermark so restore can select a compatible app/schema path.

## Operational recovery

Phase 1 reports an encrypted-database initialization error and does not fall back to browser storage or erase data. Automated backup, migration rollback packages, snapshot restore, and customer recovery support are Phase 2 operational deliverables.
