# RWP Phase A PostgreSQL 18 preflight results

Date: 29 October 2026 (final server UTC execution window 18:15–18:16)

Final status: **PASS — PostgreSQL preflight is complete; deployment was not performed.**

## Scope and isolation

The GT2 Maven descriptor and corrected source tree were staged under a uniquely
generated temporary directory. Maven used a repository inside that directory.
Testcontainers started a separate disposable `postgres:18` container and its
Ryuk helper. The existing `gapps-postgres` container was not used by the tests,
and no GT2 database, role, service, persistent directory, port, or Tailscale
route was created.

Before editing V1, the server was checked for migration-history compatibility:
no persistent GT2 database or role, `/home/gutta/gapps/gt2` directory, or
`gt2-backend` service existed. V1 therefore had not been applied to a persistent
GT2 database that required a follow-up migration.

## Targeted corrections

The five undeployed hash columns whose JPA `String` mappings specify
`length = 64` were changed from `CHAR(64)` to `VARCHAR(64)`:

| Table | Column | JPA entity field |
|---|---|---|
| `enrollment_codes` | `code_hash` | `EnrollmentCode.codeHash` |
| `devices` | `token_hash` | `Device.tokenHash` |
| `sync_batches` | `request_hash` | `SyncBatch.requestHash` |
| `processed_sync_events` | `request_hash` | `ProcessedSyncEvent.requestHash` |
| `backup_snapshots` | `sha256` | `BackupSnapshot.sha256` |

No other migration column or application behavior was changed. During the
first corrected-schema rerun, Flyway and Hibernate passed, but Spring Boot's
Jackson 3 configuration rejected the legacy serialization-feature namespace.
The date serialization setting was moved to the Jackson 3 `datatype.datetime`
namespace while preserving `write-dates-as-timestamps: false` behavior.

## Final Maven test result

| Metric | Result |
|---|---:|
| Tests executed | 5 |
| Passed | 5 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |

- `Gt2PostgresIntegrationTest`: 3 passed, 0 skipped.
- `SecretHashingTest`: 1 passed.
- `SyncServiceTest`: 1 passed.
- Maven result: `BUILD SUCCESS` in 12.032 seconds.

All three integration methods completed their assertions:

- tenant-isolated snapshot download and checksum verification;
- one-use device registration plus authenticated API protection;
- ordered/idempotent patient-event processing and missing-predecessor rejection.

## PostgreSQL, Flyway, and Hibernate evidence

- Test image: `postgres:18`.
- Runtime database version: PostgreSQL 18.6.
- Flyway successfully validated one migration against an empty schema.
- Flyway created `flyway_schema_history` and successfully applied
  `V1__create_gt2_backup_schema.sql`, reaching schema version 1.
- Hibernate `ddl-auto=validate` completed and initialized the JPA
  `EntityManagerFactory`; no schema-validation exception occurred.
- The Spring application context and random-port embedded server started, after
  which all three PostgreSQL integration methods passed.

## JAR result

- Artifact: `backend/target/gt2-backend-1.0.0-SNAPSHOT.jar`
- Size: 60,051,952 bytes.
- SHA-256:
  `abde9b2203b93f9d3fca2c53e797655f0eca579711a9e6aaa992936770358aeb`

## Existing-service verification

Before and after the preflight:

- `gapps-postgres` retained container ID
  `5d17d197387f6ef918d283e0554068819fbda1484bf987315482b8224659fec7`,
  the same start timestamp, healthy status, image, and loopback port binding.
- Its database catalog remained `gdc` and `postgres`; no `gt2` database or
  `gt2_app` role appeared.
- `gdc-backend` remained active with PID 2347 and the same start timestamp.
- `gdclab-backend` remained active with PID 2402 and the same start timestamp.
- Both existing application JAR SHA-256 values were unchanged.
- Ports 5432, 8080, and 8090 retained their original listeners; 8091 remained
  unused.
- GDC's patient API continued returning HTTP 200. GDCLab retained its original
  service, port, and HTTP behavior.

No existing service was stopped, restarted, reconfigured, or written to.

## Cleanup verification

- Disposable PostgreSQL and Ryuk containers: removed.
- Testcontainers networks and volumes remaining: zero.
- Ryuk helper image pulled solely for the rerun: removed by exact image ID.
- Temporary source, build output, reports, log, and isolated Maven cache:
  removed using the validated exact temporary-directory path.
- Persistent GT2 server resources remaining: none.

## Approval boundary

Phase A passed, but it did not authorize or perform GT2 database provisioning,
installation, service startup, Nginx changes, Tailscale configuration, or
firewall changes. Those actions still require separate explicit approval.
