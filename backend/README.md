# GT2.0 backend

Java 21/Spring Boot 4.1.2 service for authenticated, tenant-isolated incremental backup and clean-device recovery. PostgreSQL is required; Flyway owns the schema. This service is not the tablet's live operational database.

## Local prerequisites

- JDK 21 and Maven 3.6.3+
- PostgreSQL 17 (or a supported recent PostgreSQL) with an empty database
- A writable snapshot directory
- Docker/Podman only if running the Testcontainers suite

Copy `.env.example` values into your shell or IDE environment. Generate secrets rather than using the examples:

```bash
export GT2_DB_URL='jdbc:postgresql://127.0.0.1:5432/gt2'
export GT2_DB_USERNAME='gt2_app'
export GT2_DB_PASSWORD='replace-me'
export GT2_ADMIN_TOKEN="$(openssl rand -hex 32)"
export GT2_SNAPSHOT_MASTER_KEY="$(openssl rand -base64 32)"
export GT2_SNAPSHOT_STORAGE="$PWD/data/snapshots"
export GT2_ALLOWED_ORIGINS='https://localhost'
mvn test
mvn spring-boot:run
```

Do not put actual secrets in `.env.example`, Git, the APK, or the systemd unit. `/actuator/health` is the only public operational endpoint.

## Provision a local test device

With the service on `http://127.0.0.1:8080`:

```bash
curl -sS -X POST http://127.0.0.1:8080/api/v1/admin/tenants \
  -H "X-GT2-Admin-Token: $GT2_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"tenantCode":"DEMO","tenantName":"Demo Clinic"}'

curl -sS -X POST http://127.0.0.1:8080/api/v1/admin/tenants/TENANT_UUID/enrollment-codes \
  -H "X-GT2-Admin-Token: $GT2_ADMIN_TOKEN"
```

Enter the returned one-use enrollment code and HTTPS server address in the tablet Sync panel. Direct HTTP registration is accepted by the client only for `localhost`; a physical tablet must use a trusted HTTPS endpoint.

## Tests and package

```bash
mvn test
mvn clean package
java -jar target/gt2-backend-1.0.0-SNAPSHOT.jar
```

The Testcontainers class skips when no container runtime is present. A green Maven result with skipped integration tests does not prove PostgreSQL/Flyway readiness; inspect the summary.

## RWP deployment preparation

The actual server was audited read-only on 29 October 2026. The reviewed plan
uses `/home/gutta/gapps/gt2`, isolated database/role `gt2`/`gt2_app`, loopback
port 8091, `/etc/gt2/backend.env`, a dedicated `gt2` runtime identity, and
private HTTPS through Tailscale Serve. Existing Nginx, GDC, GDCLab, ports
8080/8090, and database `gdc` are outside the deployment scope.

See [`deploy/README.md`](deploy/README.md) for the audit, exact approval-gated
commands, protected snapshot layout, health checks, backup, and rollback. The
scripts default to a dry run or refuse mutation unless `--apply` is supplied.
No RWP deployment was performed during preparation.
