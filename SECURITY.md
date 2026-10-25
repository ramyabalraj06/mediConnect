# Patient Records Security Contract

## Runtime and configuration

This implementation retains the existing Java 17 / Spring Boot 4.1.2 build.
The documented Java 21 / Boot 3.x baseline still requires an explicit platform
decision before release; this change does not claim to resolve that mismatch.

Required environment variables:

| Variable | Value |
| --- | --- |
| PATIENT_ENCRYPTION_KEY | Secret, Base64-encoded random 32-byte AES key; preserve across restarts. |
| JWT_PUBLIC_KEY | Base64-encoded X.509 DER RSA public key, at least 2048 bits. This is not a private key or JWT secret. |
| JWT_ISSUER | Exact trusted issuer identifier. |
| JWT_AUDIENCE | Expected audience for this service. |

Obtain the verification key, issuer, and audience from the trusted identity
provider. The application neither issues tokens nor downloads keys from
token-controlled headers. Private signing keys must never be installed here.
Run `.\mvnw.cmd spring-boot:run` after supplying configuration. All patient
requests require an `Authorization: Bearer` header; there is no cookie/session
authentication. TLS must be enforced by the deployment ingress.

## JWT policy

- Only RS256 is accepted, with signature verification using Nimbus JOSE.
- Issuer, audience, subject, `exp`, `iat`, `nbf`, `clinic_id`, `clinic_ids`,
  `role`, and `scope` are required. Temporal claims must be nonnegative integral
  NumericDate values. `iat <= nbf < exp` and `iat < exp` are required.
- Clock skew defaults to 30 seconds and is limited to 0 through 60 seconds via
  `mediconnect.security.clock-skew-seconds`. At the expiry boundary, the token
  is expired; future issuance/activation beyond the allowance is rejected.
- `clinic_id` must be a canonical UUID in the signed `clinic_ids` assignment
  array. The identity provider must validate assignments before issuing tokens.
- Roles are `PATIENT`, `CLINICIAN`, or `ADMIN`. Scopes are space-delimited;
  patient operations require `patients:create`, `patients:read`, or
  `patients:write` as appropriate.
- Optional `patient_ids` is an explicit, clinic-specific resource grant array
  of canonical patient UUIDs. The issuer must populate it from an approved
  authorisation source, never copy client assertions into it. Keep token
  lifetimes short; grant/assignment revocation takes effect at token expiry
  unless the deployment provides additional revocation infrastructure.

The service independently enforces authenticated clinic context, operation
scope, role, and record access. Creating staff acquire access to the record
they create. Other staff need a signed resource grant. Patients need a signed
resource grant and may read only. Administrators have no implicit access to
every record. This deny-by-default policy requires clinical/security approval
before use with real patient data. No grant-management or admin endpoint is
provided. Existing records must not be assigned an arbitrary creator.

## API

| Method and path | Request | Success |
| --- | --- | --- |
| POST /api/patients | firstName, lastName, email | 201 with Location and patient response |
| GET /api/patients/{id} | Canonical UUID path, no query parameters | 200 |
| PUT /api/patients/{id} | firstName, lastName, email, version | 200; stale version returns 409 |
| DELETE /api/patients/{id} | Canonical UUID path, no body or query parameters | 204, including repeat authorised deletion |

The patient package is `com.demo.mediconnect.patient`. PatientController handles
HTTP input/output only and uses scope-based `@PreAuthorize` checks with method
security enabled. PatientMapper explicitly maps PatientCreateRequest and
PatientUpdateRequest to the existing internal PatientRequest command and maps
entities to PatientResponse. The service retains clinic, role, permission,
optimistic-locking, audit, and deletion rules; repositories handle persistence.
No request body binds directly to Patient, and no response serializes an entity.
Soft deletion uses deletedAt/deletedBy; no redundant deactivated flag is needed.

Responses contain only id, firstName, lastName, email, and version. Use the last
returned version when updating. Unknown/forbidden body fields are rejected
with `400 VALIDATION_ERROR`, not silently ignored. Responses are not cacheable.
There is no list/search endpoint. Future lists must use bounded cursor/limit,
nextCursor/hasMore, tenant/filter-bound cursors, and a unique ordering tie-breaker.

Authentication failures return 401 with `AUTH_INVALID_TOKEN`, `AUTH_INVALID_EXP`,
`AUTH_TOKEN_EXPIRED`, `AUTH_INVALID_IAT`, `AUTH_INVALID_NBF`, or
`AUTH_TOKEN_NOT_ACTIVE`. Missing scope/role returns 403 with
`AUTH_FORBIDDEN_INSUFFICIENT_SCOPE` or `AUTH_FORBIDDEN_INSUFFICIENT_ROLE`.
Missing, deleted, wrong-clinic, and resource-denied reads/updates return the
same `404 PATIENT_NOT_FOUND`. Resource-denied deletion also returns that 404;
an authorised repeat deletion returns 204. Conflicting versions return
`409 PATIENT_VERSION_CONFLICT`. Exceptions, claims, and request bodies are not
included in error responses or rejection logs.

## Persistence and audit

Flyway applies the initial schema to a fresh database; Hibernate validates it
and does not modify it automatically. Schema migrations contain DDL only;
application data operations use JPA. Flyway automatic baselining is not enabled.
An existing nonempty database without Flyway history will fail startup rather
than silently adopt a schema or guess ownership. Before upgrading such a
database, back it up and have its schema, actor backfill, version initialisation,
and migration/baselining plan reviewed. Do not delete it to bypass validation.

Delete marks deletedAt/deletedBy; active reads and updates exclude those rows.
The restricted repository interface exposes neither unscoped reads nor physical
deletion. JPA versioning detects stale writes. Successful create, read, update,
and first deletion persist an audit event in the same transaction; a failed
audit write rolls back the operation. Actor identifiers are encrypted, as are
names and email. Rejections produce structured logs with stable error codes
and no patient payload or token. Protect audit tables and rejection-log access
with deployment-level permissions; the application audit trail alone is not
an independently tamper-proof compliance ledger.

H2 file permissions, encrypted backups, recovery exercises, key custody/rotation,
retention, legal holds, eventual secure erasure, and production database approval
remain operational/security release gates. Soft deletion is not secure erasure.
The existing encryption envelope uses one unversioned key; changing or losing
it makes existing fields unreadable. Key rotation needs an approved migration.
No HIPAA or other regulatory compliance claim is made by these changes.

## Verification

Run `.\mvnw.cmd clean verify`. Tests use synthetic identities, generated keys,
local HTTP, and isolated in-memory H2 databases with the real migrations.
JaCoCo enforces 80% service line coverage and 70% controller/security coverage.
The report is in target/site/jacoco/index.html. Security tests exercise algorithm
rejection, temporal claims, tenant assignment, same-clinic BOLA, explicit grants,
mass assignment, soft deletion, stale updates, and persistence. Appointment
recovery jobs and their indexes/tests are outside this patient-only change.
PatientControllerTests provides executable MockMvc examples for all four
operations and 400, 401, 403, and 404 responses, with the real JWT filter chain.