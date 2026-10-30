# Phase 1 test plan

## Objectives

Verify that Phase 1 is genuinely offline/native, preserves reference Patient rules, protects local access, commits outbox data atomically, packages for Android, and clearly separates deferred work.

Only synthetic records may be used. Test credentials are supplied to the emulator harness through environment variables and must not be committed or printed in reports.

## Static and unit checks

| Area | Checks |
|---|---|
| Type/build | Strict Angular compilation and optimized production bundle |
| Passwords | Random salted digest, matching verification, wrong-password rejection |
| Validation | normalization, doctor conditional rule, mobile, fee precision/range |
| Atomic database API | one `executeSet` call with transaction enabled; native failure propagation |
| Patient repository | sequence, Patient, and outbox statements share one batch |
| Schema | version-1 SQL parses on SQLite; constraints/indexes present |
| Android | Capacitor plugin detection, web asset sync, Gradle debug assembly |
| Source hygiene | no embedded test credential/secret/real patient, no unintended HTTP Patient service |

Commands:

```bash
cd frontend
npm test
npm run build
npx cap sync android
cd android
./gradlew assembleDebug
```

## Native acceptance matrix

1. Fresh application data opens Secure setup, not a default credential screen.
2. Enroll a synthetic administrator; verify the app returns to login.
3. With no backend/network dependency, login and reach Home.
4. Verify bundled logo/Home artwork, fonts, header order, Patient submenu, placeholders, Sync and Logout placement.
5. Verify protected routes reject a missing in-memory session.
6. Verify Patient form defaults and actual radio values on Android WebView.
7. Reject invalid form values and conditional missing doctor name.
8. Create one clearly synthetic Patient and capture the generated Patient number.
9. Query only counts from native SQLite: one Patient and one outbox row.
10. Find the record through Patient Details search/list and inspect all displayed fields.
11. Edit one synthetic field; verify success, new value, record version behavior, and second outbox row.
12. Attempt a duplicate first-name/mobile create; verify user-facing error and unchanged counts.
13. Force-stop/relaunch; verify login is required and edited Patient/outbox state persists.
14. Reboot device/emulator; repeat persistence verification.
15. Click Sync; verify the exact Phase 2 message and no backend request/success claim.
16. Logout; verify login route and protected-shell denial.
17. Exercise portrait/landscape on a target approximately 11-inch physical tablet.

For outbox atomicity, successful create/update counts and a native rejected duplicate transaction must be observed. The rejected batch must create neither a Patient nor an event.

## Failure and recovery checks

- Wrong password does not create a session.
- SQLite initialization failure disables login and never falls back to browser storage.
- Constraint/duplicate errors restore button state and show a bounded user message.
- App interruption leaves no half Patient/outbox transaction.
- A failed migration does not recreate or erase the existing database.
- Sync retry/acknowledgment failure tests are deferred until a Phase 2 transport exists.

## Device coverage

At least one supported Android API emulator/device is required for native sign-off. A physical 11-inch customer tablet must additionally verify touch targets, keyboard behavior, portrait/landscape, status/navigation bars, storage policy, release signing, and vendor-specific process/reboot behavior before production rollout.

## Exit criteria

- all unit tests and production/Gradle builds pass;
- native create/search/edit/duplicate/restart/reboot/outbox checks pass;
- no test is described as passed unless executed;
- deferred physical/release/server checks are listed in `TEST_RESULTS.md`.
