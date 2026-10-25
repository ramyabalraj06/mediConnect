# MediConnect Backend — Copilot Instructions (Java)

## Project Description
MediConnect is a digital health platform that connects patients with general practitioners, specialists, and allied health providers across clinic and telehealth networks.  
This backend service supports appointment lifecycle workflows, patient records access, and clinic operations in a regulated healthcare environment.

Current sprint priority is the **Missed Appointment Recovery System**:
- Detect appointments that become no-shows after appointment end time.
- Trigger personalized recovery outreach (SMS/push).
- Offer priority rebooking slots.
- Log all recovery actions for audit and care-gap analytics.
- Mark recovery successful if patient rebooks and attends within 7 days.

This project is developed with GitHub Copilot in an AI-augmented workflow, but all generated code must meet strict security, compliance, and clinical safety standards before merge.

## Technology Stack
- **Language:** Java 21
- **Framework:** Spring Boot 3.x
- **Build Tool:** Maven
- **Database:** H2
- **ORM:** Spring Data JPA (Hibernate)
- **Security:** Spring Security + JWT
- **Validation:** Jakarta Bean Validation (`jakarta.validation`)
- **Testing:** JUnit 5, Mockito, Spring Boot Test
- **API Style:** REST (JSON)
- **Architecture Pattern:** Layered architecture (`controller -> service -> repository -> model/entity`)
- **Observability:** Structured logging with correlation IDs (JSON logs preferred)

## Security, Performance & Authorisation Rules (Mandatory)

### Security Requirements
- JWT verification must explicitly pin the accepted algorithm to **RS256 or HS256 only**.
- Tokens using `alg: none` must be rejected at middleware level before any protected endpoint logic runs.
- JWT middleware must validate **signature + exp + iat + nbf** on every authenticated request.
- If JWT validation fails, return **401** with a machine-readable error code (e.g., `AUTH_INVALID_TOKEN`, `AUTH_TOKEN_EXPIRED`, `AUTH_TOKEN_NOT_ACTIVE`, `AUTH_INVALID_IAT`).
- All write endpoints must use **DTOs with explicit field whitelisting**.
- Direct entity/model binding from request body is prohibited (mass-assignment prevention).
- Validate all external inputs (path/query/body/headers) with Bean Validation.
- No raw SQL; use JPA repositories/parameterized ORM operations only.
- No hardcoded credentials, API keys, JWT secrets, or encryption keys.
- Sensitive fields must be encrypted at rest.

### Performance Requirements
- All list endpoints must implement **cursor-based pagination only**:
  - Request: `cursor`, `limit`
  - Response: `nextCursor`, `hasMore`
- Offset/page/skip pagination is prohibited.
- No-show detection scan must accept configurable `batchSize`.
- No-show scan must commit each batch independently (partial progress must persist on failure).
- No-show scan must be restartable from the last successfully processed cursor/checkpoint.
- At least **2 DB indexes** must be defined in both `SPEC.md` and ORM model metadata, including:
  - indexed columns
  - index type (B-tree/composite/partial)
  - exact query pattern supported

#### Minimum Required Indexes
1. **Composite B-tree index** on appointments: `(clinic_id, status, appointment_end_time)`  
   - Supports no-show eligibility scan by tenant + status + time window.
2. **Composite B-tree index** on recovery tracking: `(patient_id, missed_appointment_at)`  
   - Supports 7-day recovery success evaluation queries.

### Authorisation Requirements
- Authentication alone is insufficient for resource access.
- Every endpoint that fetches resource by ID must enforce ownership or explicit permission check for that specific resource (BOLA protection).
- Middleware must extract and validate `clinic_id`, `role`, and `scope` claims from JWT.
- All patient/appointment queries must be scoped by authenticated `clinic_id`.
- Cross-clinic data access is strictly forbidden unless explicitly authorized by policy.
- Admin-only endpoints (e.g., no-show scan trigger, analytics summary):
  - must reject non-admin/patient-role tokens with **403** (not 401/500)
  - must return machine-readable error code (e.g., `AUTH_FORBIDDEN_INSUFFICIENT_ROLE`)
- Authorization failures must never leak resource existence details.

## Testing Scope (Mandatory)

### Coverage Targets
- Minimum **80% line coverage** for service layer.
- Minimum **70% line coverage** for controller and security layers.
- Critical workflows (no-show detection, recovery marking, auth/authz checks) require full happy-path + negative-path tests.

### Test Types
- **Unit Tests**
  - Services, validators, mappers, utility classes.
  - Use Mockito for repository and external provider mocks.
- **Integration Tests**
  - Repository and API integration with H2 in-memory DB.
  - Verify transaction boundaries, persistence behavior, and query filtering by clinic.
- **Controller/API Tests**
  - Request validation, response shape, status codes, and machine-readable error codes.
- **Security Tests**
  - JWT signature and claim validation (`exp`, `iat`, `nbf`).
  - `alg:none` rejection.
  - Role/scope/clinic-based access control.
- **Batch/Job Tests**
  - No-show scan batch commit behavior.
  - Restart from checkpoint cursor after simulated failure.
  - Idempotent re-run behavior (no duplicate side effects).

### Mandatory Negative Test Scenarios
- Expired token -> `401 AUTH_TOKEN_EXPIRED`
- Missing/invalid `nbf` or `iat` -> `401` with specific error code
- Token with disallowed algorithm -> `401 AUTH_INVALID_TOKEN`
- Non-admin token on admin endpoint -> `403 AUTH_FORBIDDEN_INSUFFICIENT_ROLE`
- Cross-clinic resource access attempt -> `403` or `404` per endpoint policy without PHI leakage
- DTO mass-assignment attempt (extra forbidden fields) -> `400` validation error

### External Dependency Mocking
- SMS provider, push notification provider, and any third-party integration must be mocked in unit/integration tests.
- No external network calls allowed during CI test execution.

### Test Data & Compliance
- Use synthetic/anonymized test data only.
- Never use real patient identifiers in fixtures, logs, snapshots, or assertions.
- Ensure logs generated during tests do not contain PHI/PII.

### CI Quality Gates
- All tests must pass before merge.
- Coverage thresholds must be enforced in CI.
- PR must include evidence of:
  - security test execution,
  - authorization test execution,
  - no-show workflow test execution.