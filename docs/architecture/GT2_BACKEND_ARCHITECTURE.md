# GT2.0 backend architecture

## Purpose and boundary

The backend is a Java 21/Spring Boot 4 modular monolith. It receives the approved tablet outbox contract, maintains a tenant-isolated PostgreSQL backup copy, and stores encrypted logical recovery snapshots. SQLite remains the operational source of truth while offline; the server never participates in a local patient write.

## Modules

- `config`: typed environment configuration, CORS, security chain, and uniform API errors.
- `security`: SHA-256 credential lookup, constant-time comparisons, admin/device filters, and authenticated `DevicePrincipal`.
- `tenant` and `device`: tenant provisioning, one-use enrollment codes, device tokens, revocation.
- `sync`: batch/event persistence, per-event transaction processing, idempotency, ordering, and acknowledgments.
- `patient`: tenant-scoped backup projection of patient state.
- `snapshot`: metadata, AES-256-GCM file storage, retention, download integrity checks, and recovery audit.

## Trust model

An environment-only admin token protects provisioning endpoints. A one-use, expiring enrollment code returns one high-entropy device bearer token. Only the SHA-256 token digest is stored on the server; the raw token is kept inside the tablet's encrypted SQLite database. Tenant and device IDs come from the authenticated principal, never from caller-selected query parameters.

Traffic must terminate trusted TLS before reaching the application, which binds privately on loopback. The audited initial RWP plan uses tailnet-private Tailscale Serve; Caddy or a reviewed Nginx configuration remains an option for a later public endpoint. CORS is an additional browser control, not authentication. Database credentials, the admin token, and the 32-byte Base64 snapshot master key stay outside the JAR.

## Transaction and durability model

A batch header is committed first. Each event then runs in an independent `REQUIRES_NEW` transaction containing both patient mutation and processed-event marker. Results are stored independently and the completed batch is replayable. A failed event therefore cannot roll back successful siblings; an interrupted retry is safe because `(tenant_id,event_id)` and `(tenant_id,entity_type,entity_id,entity_version)` are unique.

Snapshots are UTF-8 logical JSON documents. The exact plaintext bytes are SHA-256 verified, encrypted with a unique 96-bit GCM nonce, bound to tenant/snapshot IDs as authenticated data, and written by atomic rename. Snapshot files do not contain local user credentials or device secrets.

## Deliberate limitations

- Sync is one-way tablet-to-server. There is no multi-device merge or server-to-tablet operational replication.
- Conflict policy is strict ordered version rejection, not last-write-wins.
- The application-level snapshot store complements, but does not replace, PostgreSQL backups and off-host filesystem backups.
- Token rotation and a full administrative UI are deferred; revocation is implemented.
