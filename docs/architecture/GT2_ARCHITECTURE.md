# GT2.0 architecture

## System boundary

Phase 1 is a native Android application that remains useful with no server and no internet. Angular owns presentation and business orchestration; Capacitor hosts the WebView and native bridge; encrypted SQLite is the authoritative operational database.

```text
Angular routes/components
          |
          v
Business services and validation
          |
          v
SQLite repositories --------> local authentication repository
          |
          v
single native transaction batch
     |                  |
     v                  v
Patient record       Sync outbox event
```

There is no Phase 1 HTTP Patient service. Browser storage is not a substitute. On a non-native platform, database initialization enters an explicit unsupported state.

## Source boundaries

- `core/configuration`: build-time client brand and enabled-module configuration.
- `core/database`: native connection, encryption-secret bootstrap, schema version 1, and atomic batch execution.
- `core/auth`: first-user enrollment, PBKDF2 verification, in-memory session, and route guard.
- `core/sync`: versioned outbox types only; no transport.
- `features/registration`: Patient model, validation, repository, business service, form, registration, search/details, and edit.
- `layout/app-shell`: shared header, responsive navigation, Sync placeholder, logout, and router outlet.
- `features/placeholders`: reusable deferred-module screen.
- `public/branding`: local assets copied into the native bundle.

The shell is kept as one cohesive component because its header and navigation behavior are small and tightly coupled. Splitting empty header/navigation wrappers would add indirection without reusable behavior.

## Application lifecycle

At startup, an application initializer requires Android, initializes the SQLite plugin, creates or retrieves the device-local encryption secret, opens `gt2_operational` in encrypted `secret` mode, and applies the idempotent version-1 schema. The login route then checks whether an enabled user exists. A fresh installation is redirected to Secure setup.

After a successful login, user identity exists only in an Angular signal. No session token or password is stored in browser storage. A reload, process death, application restart, or device reboot returns to login. Logout clears the signal before navigation.

## Data and transaction model

Synchronized entities have immutable UUID identifiers. `patient_number` remains a separate human-facing sequential identifier. `record_version` increases on every business change. All instants are stored as UTC ISO-8601 text; fees are stored as integer paise.

Every Patient write is sent as one `executeSet(..., true)` native call. This matters on Android because SQLite transactions are thread-affine; splitting begin/work/commit across independent Capacitor calls can move execution between native threads. A single bridge call guarantees that the sequence change, Patient row, and outbox row either commit together or roll back together.

Update/delete outbox inserts are conditional on the preceding optimistic-version write changing exactly one row. A unique-constraint or validation failure aborts the batch and creates no event.

## Security posture

- SQLite encryption is enabled in Capacitor; a 256-bit random secret is generated on-device and stored by the plugin's Android secure-secret facility.
- Android backup/device-transfer extraction is disabled for root, database, shared-preference, and external domains.
- Cleartext network traffic is disabled.
- Passwords use a per-user 128-bit salt and PBKDF2-HMAC-SHA-256 with 310,000 iterations and a constant-time byte comparison.
- SQL values are parameterized; form rules are duplicated as database constraints where SQLite supports them.
- No production credential, encryption secret, real patient data, remote URL, or signing key is committed.

Phase 1 limitations: it has no password recovery, biometric unlock, admin account lifecycle, remote revocation, attestation, or server-backed disaster recovery. Device compromise while the app is unlocked is outside the current protection model. These are not replaced with insecure shortcuts.

## Branding and responsive UI

`CLIENT_BRAND` contains names, asset paths, theme colors, labels, and enabled modules. The shell consumes this data without clinic-specific business logic. Assets and font files are bundled. CSS provides two-column tablet forms/details, landscape-friendly width limits, and a compact single-column layout below 760 px.

The chosen model is build-time client branding. Remote runtime theming is not implemented or implied.

## Phase boundary

The outbox is the only Phase 1 sync component. The Sync icon displays exactly `Sync available in Phase 2` and invokes no networking code. Phase 2 will add authenticated transport and a separate server; it must not replace SQLite as the tablet's immediate operational store.
