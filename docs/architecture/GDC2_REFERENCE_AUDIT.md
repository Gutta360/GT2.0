# GDC2.0 reference audit

## Audit scope

The supplied `/Users/gutta/Desktop/GDC2.0.zip` was inspected read-only before GT2.0 implementation. A temporary extraction was used for analysis; the ZIP and its source were not modified. Generated dependency/build folders were excluded from the working copy.

## Technology and build

The reference frontend is a standalone Angular application using Angular/CLI/build 22.2, TypeScript 6.0.2, RxJS 7.8, npm 11.16, Vitest 5, and jsdom 30. GT2.0 retained these versions and added only Capacitor, native SQLite, and locally bundled font packages.

The reference backend is Java 21 with Spring Boot 4.1.1, Spring MVC, validation, JPA, PostgreSQL, Flyway, Lombok, and Testcontainers 2.0.5. Its runtime assumes an available Spring/PostgreSQL deployment; deploy, Docker, Postman, and server-property files reflect that connected architecture.

## Shell and visual identity

The reference uses a black header, white navigation, light gray application canvas, rounded white content surfaces, Raleway headings, and Inter body text. Its fonts were obtained remotely, which is incompatible with guaranteed offline use; GT2.0 bundles the same font families locally.

The shell contains a Home icon, a Patient dropdown, Appointment, Treatment, Payment, Pharmacy, and a power/logout action. Patient children are Registration, Patient Details, and Patient Summary. The login screen, header proportions, monochrome palette, form spacing, rounded controls, dropdown behavior, and Patient presentation were used as the migration baseline.

The ZIP did not contain a reusable clinic logo/home image suitable for the new offline brand contract. GT2.0 therefore supplies local neutral SVG branding that can be replaced through client configuration.

## Patient contracts and rules

The audited files include the Angular Patient model/service/forms/details/edit screens and the backend controller, DTOs, entity, repository, service, mapper, exceptions, and `V1__create_patient_module.sql`.

Reference REST endpoints were:

- `POST /api/v1/patients`
- `GET /api/v1/patients/{patientId}`
- `GET /api/v1/patients`
- `GET /api/v1/patients/search?query=...`
- `PUT /api/v1/patients/{patientId}`
- `DELETE /api/v1/patients/{patientId}`

The relevant rules preserved offline are:

- first name: required, trimmed, 2–50 ASCII letters/spaces;
- last name: optional, at most 50 ASCII letters/spaces;
- gender: `MALE`, `FEMALE`, or `OTHER`;
- age: 1–120;
- mobile: exactly 10 decimal digits;
- address: trimmed length 2–255;
- referral: `DOCTOR`, `PATIENT`, `ONLINE`, `SELF`, or `OTHER`;
- doctor name: required for `DOCTOR`, cleared for other referral types, max 100;
- consultation fee: non-negative, at most eight integer and two fraction digits;
- registration date: required instant;
- duplicate key: normalized case-insensitive first name plus mobile;
- human patient number: `P-` plus a five-digit sequence, for example `P-00001`;
- delete: soft delete with active flag and deletion timestamp;
- update: optimistic version semantics.

The PostgreSQL reference used an identity primary key and separate `patient_id`. GT2.0 adapts this to an immutable UUID `id` for future synchronization plus a unique human `patient_number`. Monetary values are stored as integer paise to avoid floating-point persistence errors. UTC ISO-8601 text is used for instants.

The reference search service returned no results for queries shorter than two characters, while its frontend initially loaded patient data through other calls. GT2.0 supports an empty query for the native Patient Details list and searches ID/name/mobile locally, which is a deliberate usability adaptation for an entirely local data set.

## Authentication finding

The reference Angular `AuthService` contained hardcoded `admin`/`admin123` values and did not provide durable protected-session security. Those credentials and that implementation were explicitly excluded. GT2.0 replaces them with one-time local enrollment, salted PBKDF2 password verification, an encrypted database, a volatile authenticated session, a route guard, and explicit logout.

## Tests and deployment assumptions

The reference includes Angular component/service specs and Spring tests for Patient and other business modules, plus PostgreSQL Testcontainers coverage. Those tests are coupled to the reference code and were not copied blindly. GT2.0 added focused tests for password hashing, validation, native transaction batching, and Patient/outbox write composition.

The reference assumes Spring Boot/PostgreSQL availability and includes server deployment material. GT2.0 Phase 1 has no server deployment, does not deploy to RWP, and performs no Patient HTTP request.

## Migration disposition

| Reference area | Decision | GT2.0 treatment |
|---|---|---|
| Angular/TypeScript/tooling versions | Reuse | Retained and extended with Capacitor/SQLite |
| Shell, typography, palette, spacing | Adapt | Recreated responsively with bundled fonts and SVG icons |
| Patient forms/models/rules | Adapt | Preserved semantics; HTTP service replaced by business service/repository |
| PostgreSQL Patient schema | Adapt | SQLite constraints, UUID sync identity, paise, UTC text, local sequence |
| Patient REST client | Exclude in Phase 1 | No runtime backend dependency |
| Hardcoded authentication | Exclude | Secure local enrollment and PBKDF2 verification |
| Non-Patient modules | Exclude from implementation | Routes retained as polished In Progress screens |
| Patient Summary | Defer | Sub-navigation retained; implementation is In Progress |
| Remote font loading | Replace | Local `@fontsource` packages |
| Spring/PostgreSQL deployment | Defer | Phase 2 contract and plan only |

No existing source was copied as an opaque subsystem. UI and rules were migrated into a smaller offline architecture appropriate to the Phase 1 scope.
