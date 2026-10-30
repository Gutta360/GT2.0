# Phase 1 test results

Executed 29 October 2026 (Asia/Kolkata).

## Environment

- macOS host, Node.js 26.3.0, npm 11.16.0
- Angular 22.2 / TypeScript 6.0 / Vitest 5
- JDK 21, Gradle wrapper 8.14.3
- Android SDK compile/target 36, min 24
- Android 36.1 emulator, `Medium_Phone_API_36.1`, 1080 × 2274 WebView viewport
- Debug package `com.gutta.gt2`

## Automated/build results

| Check | Result | Evidence |
|---|---|---|
| Unit tests | Pass | 4 files, 7 tests |
| Angular production build | Pass | Optimized browser bundle generated in `frontend/dist/frontend` |
| Capacitor Android sync | Pass | SQLite 8.1.1 plugin detected and web assets copied |
| SQLite schema parse | Pass | Version-1 DDL executed successfully against temporary SQLite |
| Android debug build | Pass | Gradle `assembleDebug`; 126 tasks in latest native build |
| Android JVM tests | Pass | `testDebugUnitTest` |
| Android instrumentation | Pass | 1 package-identity test on Android 16 emulator; aggregate `connectedDebugAndroidTest` succeeded |
| Android lint | Pass | `lintDebug`; no new app/plugin issues (Capacitor baseline note only) |
| APK generation | Pass | `frontend/android/app/build/outputs/apk/debug/app-debug.apk` |

Final production web bundle: 370.64 kB raw initial total (92.20 kB estimated transfer). Final debug APK: 14,498,358 bytes, SHA-256 `85bb2f21591351ee36cc7bb2285b133500af83181255083c1614e92cf9cf620f`, verified with APK Signature Scheme v2 using the Android debug certificate. This debug signature is not suitable for customer release.

Unit coverage executed:

- PBKDF2 digest and correct/incorrect password verification;
- Patient normalization, doctor-referral rule, invalid mobile, and fee precision;
- native atomic-batch transaction flag and native-error propagation;
- one-batch sequence/Patient/outbox composition.

## Native emulator results

All stored names, numbers, and addresses were deliberately synthetic.

| Scenario | Result | Observation |
|---|---|---|
| Launch/first enrollment | Pass | Secure setup created a local user; no default credential |
| Offline login | Pass | Login reached Home without a backend; local account survived reinstall/restart |
| Home branding | Pass | Bundled SVG artwork and offline-ready presentation rendered |
| Navigation | Pass | Home, Patient submenu, and deferred module routes opened |
| Android form controls | Pass | Gender/referral radios exposed correct explicit enum values |
| Patient create | Pass | Generated `P-00001`; native Patient/outbox counts became 1/1 |
| Search/details | Pass | Persisted synthetic record listed and all details rendered |
| Patient edit | Pass | Address update succeeded; outbox count became 2 |
| Duplicate handling | Pass | Duplicate first-name/mobile showed reference-style error; counts stayed 1/2 |
| Atomic rollback | Pass | Rejected duplicate batch added neither Patient nor outbox row |
| App cold restart | Pass | Login required; edited record and both events persisted |
| Device reboot | Pass | After Android boot, login required and edited record/events remained 1/2 |
| Encrypted open | Pass | Native log confirmed encrypted database keying returned success |
| Sync placeholder | Pass | Control displays `Sync available in Phase 2`; no transport is implemented |
| Logout/protected route | Pass | Logout returned to login; shell guard requires in-memory session |

During acceptance testing, two native-only defects were found and fixed: radio values generated through template iteration were serialized as `on` by the Android WebView, and native Promise completions needed explicit Angular change-detection notification. Transaction writes were also consolidated into one native `executeSet` call to respect Android SQLite thread affinity. The full create/edit/duplicate/restart/reboot sequence was repeated after fixes.

## Not executed / remaining handoff checks

- A physical approximately 11-inch customer tablet was not available. The responsive CSS and phone-profile emulator were checked, but physical portrait/landscape, soft keyboard, touch ergonomics, and vendor behavior remain to be tested.
- Release signing, Play/private distribution, R8 release build, upgrade from a production v1 database, and customer keystore handling were not configured or tested.
- Biometric login, password recovery, device loss/reprovisioning, and app-data-clear recovery do not exist in Phase 1.
- Phase 2 server authentication, tenant isolation, uploads, retries, acknowledgments, retention, snapshots, restore, server backup, and disaster-recovery drills are contract/plan only.
- Patient repository DELETE/outbox semantics are implemented, but no Phase 1 delete UI was requested and no native delete UI test was run.
- Accessibility was considered with labels/roles and scalable layout, but no formal screen-reader or external accessibility audit was run.

## Quality-gate conclusion

G1, G2, G3, G5, and G6 are complete. G4 is complete for debug build and feasible emulator validation, with physical 11-inch/release-distribution checks explicitly remaining before production deployment. No RWP or other server deployment occurred.
