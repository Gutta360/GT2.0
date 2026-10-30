# Phase 1 implementation

## Delivered

### Foundation and Android

- Angular 22.2 strict standalone application in `frontend`.
- Capacitor 8.6 Android project, package `com.gutta.gt2`.
- Native encrypted SQLite plugin 8.1.1.
- Android SDK configuration: min 24, compile/target 36.
- Cleartext traffic disabled and application/database backup extraction excluded.
- Generated artifacts, local SDK paths, credentials, signing keys, and local settings ignored.

### UI and branding

- GDC-style black header, Home icon, Patient dropdown, module ordering, and power action.
- Patient children: Registration, Patient Details, Patient Summary.
- Local Raleway/Inter fonts and local SVG client/Home assets.
- Build-time app name, business name, colors, labels, enabled modules, and asset paths.
- Responsive Patient forms/details and reusable In Progress screen.
- Appointment, Treatment, Payment, Pharmacy, and Patient Summary intentionally remain placeholders.

### Authentication

- Safe first-run local administrator enrollment; no default credential.
- Minimum 12-character passphrase and validated username.
- PBKDF2-HMAC-SHA-256, 310,000 iterations, random per-user salt.
- Encrypted credential row, memory-only session, guarded application shell, logout.
- Restart and reboot behavior: session is cleared; credentials remain and login is required.

### Patient

- Create, list/search by ID/name/mobile, select/details, and edit.
- Reference validation and conditional doctor-referral rule.
- UUID identity, `P-00001`-style local sequence, UTC timestamps, integer-paise fee.
- Case-insensitive first-name plus mobile duplicate constraint.
- Optimistic record version and repository-level soft delete/tombstone support.
- Success/error feedback that updates correctly after native bridge promises.

### Sync foundation

- Durable outbox with UUID event identity and contract version 1.
- CREATE, UPDATE, and DELETE event semantics.
- Patient/sequence/outbox operations committed in a single native transaction batch.
- Pending events survive process and device restart.
- Sync icon immediately beside Logout; placeholder only, no backend call.

## Implementation decisions and deviations

- Angular's `public` folder is used for static branding instead of legacy `src/assets`.
- The compact shell owns both header and navigation rather than introducing empty wrapper components.
- PostgreSQL numeric IDs were replaced by UUID sync IDs plus separate human Patient numbers.
- Fees use paise instead of SQLite floating point.
- Search permits an empty query so Patient Details can show the on-device list.
- Native transaction work is intentionally issued in one plugin call to preserve Android thread affinity.
- The reference's hardcoded authentication and all Patient HTTP dependencies were excluded.

## Quality gates

| Gate | Status | Evidence |
|---|---|---|
| G1 UI Ready | Complete | Migrated shell, auth, Home, Patient routes, placeholders |
| G2 Offline Ready | Complete | Encrypted SQLite and local auth verified without backend |
| G3 Patient Ready | Complete | Native create/search/details/edit/duplicate/persistence checks |
| G4 Android Ready | Complete with device-scope note | Debug APK and Android 36.1 emulator validated; physical 11-inch tablet remains a handoff test |
| G5 Sync Foundation Ready | Complete | Atomic outbox counts and restart/reboot persistence verified |
| G6 Documentation Ready | Complete | Required architecture/database/API/testing documents included |

See `docs/testing/TEST_RESULTS.md` for exact executed checks and unverified items.
