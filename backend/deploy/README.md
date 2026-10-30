# GT2.0 RWP deployment preparation

Status: prepared from a read-only audit on 29 October 2026. Nothing in this
directory has been executed on the server. Every mutating helper requires an
explicit `--apply` argument.

Phase A update: after the approved targeted correction of five undeployed V1
hash columns to `VARCHAR(64)`, the disposable PostgreSQL 18.6 rerun passed all
5 tests with zero failures, errors, or skips. Flyway applied V1 and Hibernate
schema validation passed. No deployment or persistent GT2 resource was created. See
[`../../docs/testing/RWP_PHASE_A_PREFLIGHT_RESULTS.md`](../../docs/testing/RWP_PHASE_A_PREFLIGHT_RESULTS.md).

## Audited server facts

- Host: `rwp-server`, Ubuntu 26.04 LTS, Java 21.0.12.1.
- Maven 3.9.12 is installed; approximately 5.6 GB memory was available during
  the audit, which is sufficient for an isolated Testcontainers preflight.
- GApps root: `/home/gutta/gapps`; only `postgres/` currently exists there.
- PostgreSQL: healthy `gapps-postgres` container, PostgreSQL 18.6, bound only to
  `127.0.0.1:5432`, persistent Docker volume, `unless-stopped` restart policy.
- Existing database/role: `gdc` / `gdc_user`. No `gt2` database or `gt2_app`
  role exists. Secret values were neither read nor recorded.
- Existing services: `gdc-backend` is active on 8080 and `gdclab-backend` is
  active on 8090. Port 8091 was free at audit time.
- Nginx: port 80 serves GDC and proxies `/api/` to 8080 and `/gdclab/api/` to
  8090. This plan makes no Nginx change.
- Tailscale: active as `rwp-server.tail203f6d.ts.net`; no Serve configuration
  exists. Initial GT2 access will use private Tailscale HTTPS, never Funnel.
- Storage: approximately 83 GB free. Passwordless sudo is not available, so
  approved installation commands require an interactive sudo session.

## Selected isolated layout

| Purpose | Audited deployment value |
|---|---|
| Application root | `/home/gutta/gapps/gt2` |
| Releases | `/home/gutta/gapps/gt2/releases/<sha-prefix>` |
| Active JAR | `/home/gutta/gapps/gt2/current/gt2-backend.jar` |
| Runtime identity | dedicated system user/group `gt2` |
| Environment | `/etc/gt2/backend.env`, `root:root`, mode 0600 |
| Database | `gt2` in existing `gapps-postgres` container |
| Database role | `gt2_app`, login-only and owner of `gt2` |
| Application listener | `127.0.0.1:8091` |
| Snapshot store | physical `/home/gutta/gapps/gt2/shared/snapshots`, runtime `/var/lib/gt2/snapshots`, `gt2:gt2`, mode 0700 |
| Backups | `/home/gutta/gapps/gt2/shared/backups`, root-controlled |
| systemd unit | `gt2-backend.service` |
| Tablet URL | `https://rwp-server.tail203f6d.ts.net` through Tailscale Serve |

The application cannot connect to `gdc` and receives no GDC/GDCLab role. The
container remains loopback-only. GT2 binds loopback-only, so 8091 is not a new
LAN or public listener. Because `/home/gutta` is mode 0750, systemd bind-mounts
only the current JAR and snapshot directory into private runtime paths; the
`gt2` user is not added to the `gutta` group and cannot browse the home tree.

## Required pre-deployment validation

The packaged JAR contains Spring Boot 4.1.2, Flyway 12.4.0's PostgreSQL module,
and PostgreSQL JDBC 42.7.14. The integration test now defaults to
`postgres:18-alpine`, matching the audited major version.

Before deployment, run on a machine with Docker/Podman and confirm that all
three integration tests execute rather than skip:

```bash
cd backend
GT2_TEST_POSTGRES_IMAGE=postgres:18-alpine mvn clean test
mvn clean package
sha256sum target/gt2-backend-1.0.0-SNAPSHOT.jar
```

The RWP host already has Maven, Java, Docker, and the exact `postgres:18`
image. After approval, but before creating any GT2 runtime/database resource,
the safest exact preflight is to stage only `pom.xml` and `src/` in a temporary
user-owned directory and run Testcontainers against a new disposable container:

```bash
ssh gutta@100.65.174.126 'mkdir -p /home/gutta/gt2-preflight'
scp backend/pom.xml gutta@100.65.174.126:/home/gutta/gt2-preflight/
scp -r backend/src gutta@100.65.174.126:/home/gutta/gt2-preflight/
ssh gutta@100.65.174.126 \
  'cd /home/gutta/gt2-preflight && GT2_TEST_POSTGRES_IMAGE=postgres:18 mvn test'
```

Testcontainers creates a separate temporary PostgreSQL container. It does not
connect to or migrate `gapps-postgres`.

Additional integration coverage recommended before production use:

- full CREATE → UPDATE → DELETE sequence against PostgreSQL 18;
- partial-batch commit/rollback and database-constraint failure behavior;
- invalid payload and event-ID/content conflict handling;
- device revocation and cross-tenant sync access, not only snapshot access;
- service restart followed by duplicate-event replay;
- snapshot retention deletion and recovery after post-snapshot updates.

These gaps do not authorize using the existing GDC database for tests.

## Exact deployment sequence after separate approval

First stage these reviewed scripts; this is not authorized by the current
preparation task:

```bash
scp -rp backend/deploy gutta@100.65.174.126:/home/gutta/gt2-deploy
ssh -t gutta@100.65.174.126
```

On the server, inspect the state again, then prepare only GT2 resources:

```bash
/home/gutta/gt2-deploy/audit-rwp.sh
sudo /home/gutta/gt2-deploy/prepare-host.sh --apply
sudo /home/gutta/gt2-deploy/provision-database.sh --apply
sudo /home/gutta/gt2-deploy/install-systemd.sh --apply
```

On the trusted build Mac, stage the existing JAR without activating it:

```bash
backend/deploy/transfer-release.sh --apply
```

The transfer script prints the checksum-locked server command. Run that exact
`install-release.sh --apply ... SHA256` command. It updates only GT2 release
symlinks and deliberately does not restart any service.

Start and validate only GT2:

```bash
sudo /home/gutta/gt2-deploy/start-service.sh --apply
/home/gutta/gt2-deploy/health-check.sh
/home/gutta/gt2-deploy/logs.sh 100
```

The first successful start runs Flyway only in database `gt2`. Confirm health,
the Flyway history table, and the expected GT2 tables before provisioning a
tenant or device.

After loopback health succeeds, enable private HTTPS:

```bash
/home/gutta/gt2-deploy/configure-tailscale-serve.sh --apply
tailscale serve status
```

Install Tailscale on the test tablet, join the same tailnet, and enter
`https://rwp-server.tail203f6d.ts.net` in the Sync panel. Do not use the raw
100.x address because the Android client requires trusted HTTPS. Do not run
`tailscale funnel`.

## Health and verification

```bash
curl --fail http://127.0.0.1:8091/actuator/health
systemctl status gt2-backend --no-pager
journalctl -u gt2-backend -n 100 --no-pager
ss -H -ltn '( sport = :8091 )'
docker exec gapps-postgres sh -lc 'psql -U "$POSTGRES_USER" -d gt2 -c "TABLE flyway_schema_history"'
```

The last command prints migration metadata only. Never place application or
container credentials directly in a command line or report.

## Backup and rollback

Before every later release, create a consistent GT2-only backup set:

```bash
sudo /home/gutta/gt2-deploy/backup-state.sh --apply
```

It briefly stops only GT2, dumps only database `gt2`, archives encrypted
snapshot files, preserves the protected environment/key, then restores GT2 to
its previous active state. Copy the resulting root-only directory to encrypted
off-host storage.

For an application-only rollback:

```bash
sudo /home/gutta/gt2-deploy/rollback-release.sh --apply
```

This switches only the GT2 release symlink and restarts only GT2. It never
reverses Flyway. Restoring a database dump is intentionally not automated
because it overwrites data and requires separate approval and a migration
compatibility review.

For first-deployment shutdown, without deleting data:

```bash
sudo systemctl disable --now gt2-backend
/home/gutta/gt2-deploy/disable-tailscale-serve.sh --apply
```

Leave the `gt2` database, role, snapshots, environment, and releases intact for
diagnosis. Removing them is a separate destructive operation and is not part of
rollback.

## Risks and approval boundary

- PostgreSQL 18 is supported by the packaged Flyway generation, but the actual
  PostgreSQL 18 integration suite must still execute before deployment.
- Enabling Tailscale Serve may require one-time tailnet HTTPS consent. It is
  tailnet-private and subject to tailnet ACLs; it does not replace device-token
  authentication.
- The master snapshot key is required to recover stored snapshots. Back it up
  separately and never rotate it without a re-encryption plan.
- Flyway migrations are forward changes. Always capture a GT2 backup before a
  later upgrade and review migration/JAR backward compatibility before rollback.
- No command in this document authorizes changes to Nginx, UFW, `gdc`,
  `gdc_user`, GDC/GDCLab units, their JARs, or ports 8080/8090.
