# MediConnect

Spring Boot backend for patient records. The current module provides authenticated
patient creation, retrieval, updates, and soft deletion with clinic-scoped access,
explicit record permissions, encrypted patient fields, and transactional auditing.

## Current Stack

| Component | Implementation |
| --- | --- |
| Java | 17, as configured in the Maven build |
| Spring Boot | 4.1.2 |
| HTTP API | Spring MVC, JSON |
| Persistence | Spring Data JPA / Hibernate, H2, Flyway |
| Security | Spring Security resource server, RS256 JWT verification |
| Validation | Jakarta Bean Validation and strict unknown-field rejection |
| Tests | JUnit, Mockito, Spring Boot Test, MockMvc, real HTTP integration tests |
| Coverage | JaCoCo: 80% service and 70% controller/security line coverage gates |

The proposed Java 21 / Spring Boot 3.x baseline is not implemented. Platform
alignment requires a separate decision; see [pom.xml](pom.xml) for the actual build.

## Project Layout

```text
src/main/java/com/demo/mediconnect/
  MediconnectApplication.java
  patient/
    PatientController.java
    PatientCreateRequest.java
    PatientUpdateRequest.java
    PatientResponse.java
    PatientMapper.java
    PatientRequest.java
    PatientService.java
    PatientRepository.java
    Patient.java
    PatientAuditEvent.java
    PatientAuditRepository.java
    PatientFieldEncryptionConverter.java
    PatientNotFoundException.java
    PatientExceptionHandler.java
  security/
    PatientJwtDecoder.java
    PatientAccess.java
    SecurityConfiguration.java
    SecurityErrorHandler.java
src/main/resources/
  application.properties
  db/migration/V1__patient_records.sql
src/test/java/com/demo/mediconnect/
  MediconnectApplicationTests.java
  PatientApiTests.java
  TestIdentity.java
  patient/PatientControllerTests.java
  patient/PatientServiceTests.java
  security/PatientJwtDecoderTests.java
```

## Architecture

The request flow is **Controller -> Service -> Repository -> Entity**.

- The controller validates HTTP inputs, maps DTOs, and returns response DTOs.
- The mapper explicitly translates create/update DTOs to the internal
  `PatientRequest` service command and entities to `PatientResponse`.
- The service enforces clinic context, scopes, roles, resource permissions,
  optimistic locking, audit events, and deletion rules.
- Repositories provide scoped JPA access without exposing physical deletion or
  inherited unscoped patient-list operations.
- The entity defines persistence mappings and encrypted patient/actor fields.

Constructor injection is used for application dependencies. Request bodies never
bind directly to entities, and API responses never serialize entities.

## Local Setup

Install a JDK compatible with the configured Java 17 build. Maven is supplied by
the wrapper; a separate Maven installation is not required. First-time builds
need access to the configured Maven repositories.

Supply these environment variables through your local environment or secret
manager before starting the application:

| Variable | Required value |
| --- | --- |
| `PATIENT_ENCRYPTION_KEY` | Secret Base64-encoded random 32-byte AES key; preserve across restarts. |
| `JWT_PUBLIC_KEY` | Base64-encoded X.509 DER RSA public key, at least 2048 bits. |
| `JWT_ISSUER` | Exact trusted identity-provider issuer. |
| `JWT_AUDIENCE` | Expected audience for this backend. |

Obtain JWT configuration and signed access tokens from the trusted identity
provider. This application does not issue tokens. Do not install private signing
keys, print tokens, or commit secrets. Changing or losing the encryption key
makes existing encrypted fields unreadable.

From the project root in PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

The default local address is `http://localhost:8080`. Every patient request
requires `Authorization: Bearer <access-token>`. Enforce TLS at the deployment
ingress; local HTTP is not a production transport configuration.

## Patient API

| Method | Path | Required scope | Success |
| --- | --- | --- | --- |
| POST | `/api/patients` | `patients:create` | 201, response DTO and Location header |
| GET | `/api/patients/{id}` | `patients:read` | 200, response DTO |
| PUT | `/api/patients/{id}` | `patients:write` | 200, response DTO |
| DELETE | `/api/patients/{id}` | `patients:write` | 204, including repeated authorised deletion |

Use canonical UUID path values. Query parameters are not supported. DELETE must
not include a request body. There is no patient list/search endpoint; future list
APIs must implement cursor-based pagination rather than offset/page pagination.

### Create Request

```json
{
  "firstName": "Test",
  "lastName": "Patient",
  "email": "synthetic@example.invalid"
}
```

Names are required and limited to 100 characters. Email is required, must be a
valid email address, and is limited to 254 characters. Unknown or forbidden
fields, including client-supplied IDs, clinic ownership, and audit/deletion
metadata, are rejected with 400.

### Update Request

```json
{
  "firstName": "Updated",
  "lastName": "Patient",
  "email": "updated@example.invalid",
  "version": 0
}
```

PUT replaces all three editable fields. Supply the latest nonnegative `version`
returned by the API; a stale version returns 409 rather than silently overwriting
another update.

### Patient Response

```json
{
  "id": "123e4567-e89b-42d3-a456-426614174000",
  "firstName": "Test",
  "lastName": "Patient",
  "email": "synthetic@example.invalid",
  "version": 0
}
```

These examples are synthetic. Responses exclude clinic ownership and audit
metadata and use `Cache-Control: no-store`.

## Authentication and Authorisation

Only RS256 JWTs are accepted. Verification requires the signature, trusted issuer
and audience, subject, `exp`, `iat`, `nbf`, `clinic_id`, `clinic_ids`, `role`, and
`scope`. The selected clinic must belong to the signed clinic-assignment array.
Clock skew defaults to 30 seconds and is bounded to 0-60 seconds.

Creating clinicians/admins acquire access to their created records. Other staff
need an explicit signed `patient_ids` resource grant. Patients need that grant
and may read only. Administrators do not bypass resource permissions. Both the
controller and service enforce controls; accepting a clinic ID from a client is
not proof of authorisation.

The complete claim contract, access policy, and remaining approval requirements
are documented in [SECURITY.md](SECURITY.md).

## Error Responses

Errors contain a machine-readable `code`, without PHI, raw tokens, or exception
details. For example:

```json
{"code": "PATIENT_NOT_FOUND"}
```

| HTTP status | Codes |
| --- | --- |
| 400 | `VALIDATION_ERROR` |
| 401 | `AUTH_INVALID_TOKEN`, `AUTH_INVALID_EXP`, `AUTH_TOKEN_EXPIRED`, `AUTH_INVALID_IAT`, `AUTH_INVALID_NBF`, `AUTH_TOKEN_NOT_ACTIVE` |
| 403 | `AUTH_FORBIDDEN_INSUFFICIENT_SCOPE`, `AUTH_FORBIDDEN_INSUFFICIENT_ROLE` |
| 404 | `PATIENT_NOT_FOUND` |
| 409 | `PATIENT_VERSION_CONFLICT` |

Missing records and wrong-clinic/resource-denied requests use the same 404 code.
Normal reads and updates also return 404 for soft-deleted records; repeated
deletion remains successful only for an authorised caller.

## Database and Audit

H2 persists local data at `./data/mediconnect`. Flyway creates the schema for a
fresh database, and Hibernate validates it rather than automatically changing
it. The migration is
[V1__patient_records.sql](src/main/resources/db/migration/V1__patient_records.sql).
Application data access uses JPA, not raw SQL.

Automatic Flyway baselining is disabled. An existing nonempty database without
migration history needs a reviewed backup, schema migration, and actor/version
backfill plan. Do not delete existing records to bypass migration validation.

Deletion sets `deletedAt` and `deletedBy`; it does not physically remove the row.
Successful creation, reads, updates, and first deletion persist audit events in
the same transaction. Names, email, and actor identifiers use AES-256-GCM field
encryption. Filesystem permissions, audit access, backup protection, retention,
secure erasure, and key rotation still require operational controls. Soft
deletion is not secure erasure or a compliance certification.

## Build and Test

```powershell
.\mvnw.cmd -B -ntp clean verify
```

This compiles and packages the application, runs the tests, and checks coverage
thresholds. Test configuration generates ephemeral keys and uses synthetic
identities, isolated in-memory H2 databases, and the real Flyway migrations.
No identity-provider credentials or patient data are needed to run tests.

Coverage includes CRUD persistence, MockMvc endpoints, real HTTP authentication,
algorithm rejection, temporal claims, clinic assignment, same-clinic resource
permissions, mass assignment, soft deletion, and conflicting updates.
After verification, the HTML coverage report is at
`target/site/jacoco/index.html`.

Run just the controller tests:

```powershell
.\mvnw.cmd -B -ntp -Dtest=PatientControllerTests test
```

## Scope and Release Gates

Appointment scheduling, no-show recovery jobs, patient listing/search,
grant-management APIs, and token issuance are not implemented by this module.
Before real patient-data use, obtain approval for the access policy, platform
baseline, database deployment, retention/legal holds, migration strategy,
backup recovery, and key custody/rotation. This documentation makes no HIPAA or
other regulatory compliance claim.