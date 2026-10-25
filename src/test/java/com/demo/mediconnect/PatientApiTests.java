package com.demo.mediconnect;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.demo.mediconnect.patient.Patient;
import com.demo.mediconnect.patient.PatientAuditRepository;
import com.demo.mediconnect.patient.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PatientApiTests {

	@LocalServerPort
	private int port;

	@Autowired
	private PatientRepository repository;

	@Autowired
	private PatientAuditRepository auditRepository;

	private final HttpClient client = HttpClient.newHttpClient();
	private final JsonMapper json = JsonMapper.builder().build();
	private static final String CREATE = "{\"firstName\":\"Test\",\"lastName\":\"Patient\",\"email\":\"synthetic@example.invalid\"}";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestIdentity.configure(registry);
	}

	private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
				.header("Content-Type", "application/json");
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private String token(UUID clinic, String subject, String role, List<String> patientIds) throws Exception {
		return TestIdentity.token(TestIdentity.claims(clinic, subject, role, patientIds));
	}

	private UUID create(String token) throws Exception {
		HttpResponse<String> response = request("POST", "/patients", token, CREATE);
		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		JsonNode body = json.readTree(response.body());
		assertThat(body.has("clinicId")).isFalse();
		assertThat(body.has("createdBy")).isFalse();
		return UUID.fromString(body.get("id").asText());
	}

	@Test
	void fullLifecycleAuditsSoftDeletionAndRejectsStaleUpdates() throws Exception {
		UUID clinic = UUID.randomUUID();
		String token = token(clinic, "creator", "CLINICIAN", List.of());
		UUID id = create(token);
		String path = "/patients/" + id;
		HttpResponse<String> read = request("GET", path, token, null);
		assertThat(read.statusCode()).isEqualTo(200);
		long version = json.readTree(read.body()).get("version").asLong();
		String update = CREATE.substring(0, CREATE.length() - 1) + ",\"version\":" + version + "}";
		assertThat(request("PUT", path, token, update).statusCode()).isEqualTo(200);
		HttpResponse<String> stale = request("PUT", path, token, update);
		assertThat(stale.statusCode()).isEqualTo(409);
		assertThat(stale.body()).contains("PATIENT_VERSION_CONFLICT");
		assertThat(request("DELETE", path, token, null).statusCode()).isEqualTo(204);
		assertThat(request("DELETE", path, token, null).statusCode()).isEqualTo(204);
		assertThat(request("GET", path, token, null).statusCode()).isEqualTo(404);
		assertThat(request("PUT", path, token, update).statusCode()).isEqualTo(404);
		Patient retained = repository.findByIdAndClinicId(id, clinic).orElseThrow();
		assertThat(retained.getDeletedAt()).isNotNull();
		assertThat(auditRepository.findByPatientIdAndClinicIdOrderByOccurredAtAsc(id, clinic))
				.extracting(event -> event.getAction()).containsExactly("CREATE", "READ", "UPDATE", "DEACTIVATE");
	}

	@Test
	void deniesUnrelatedSameClinicAdminAndCrossClinicWithoutRevealingExistence() throws Exception {
		UUID clinic = UUID.randomUUID();
		String creator = token(clinic, "creator", "CLINICIAN", List.of());
		UUID id = create(creator);
		for (String other : List.of(token(clinic, "other-clinician", "CLINICIAN", List.of()),
				token(clinic, "other-admin", "ADMIN", List.of()),
				token(UUID.randomUUID(), "creator", "CLINICIAN", List.of(id.toString())))) {
			for (String method : List.of("GET", "PUT", "DELETE")) {
				String body = method.equals("PUT") ? CREATE.substring(0, CREATE.length() - 1) + ",\"version\":0}" : null;
				HttpResponse<String> denied = request(method, "/patients/" + id, other, body);
				HttpResponse<String> absent = request(method, "/patients/" + UUID.randomUUID(), other, body);
				assertThat(denied.statusCode()).isEqualTo(404);
				assertThat(denied.body()).isEqualTo(absent.body()).contains("PATIENT_NOT_FOUND");
			}
		}
		assertThat(request("GET", "/patients/" + id, creator, null).statusCode()).isEqualTo(200);
	}

	@Test
	void explicitPatientGrantAllowsReadButPatientRoleCannotWrite() throws Exception {
		UUID clinic = UUID.randomUUID();
		String creator = token(clinic, "creator", "CLINICIAN", List.of());
		UUID id = create(creator);
		String patient = token(clinic, "patient-account", "PATIENT", List.of(id.toString()));
		assertThat(request("GET", "/patients/" + id, patient, null).statusCode()).isEqualTo(200);
		HttpResponse<String> denied = request("DELETE", "/patients/" + id, patient, null);
		assertThat(denied.statusCode()).isEqualTo(403);
		assertThat(denied.body()).contains("AUTH_FORBIDDEN_INSUFFICIENT_ROLE");
		assertThat(request("POST", "/patients", patient, CREATE).statusCode()).isEqualTo(403);
		String assignedClinician = token(clinic, "assigned-clinician", "CLINICIAN", List.of(id.toString()));
		assertThat(request("PUT", "/patients/" + id, assignedClinician,
				CREATE.substring(0, CREATE.length() - 1) + ",\"version\":0}").statusCode()).isEqualTo(200);
	}

	@ParameterizedTest
	@ValueSource(strings = {"id", "clinicId", "role", "createdBy", "deletedAt", "unexpected"})
	void rejectsMassAssignmentForCreateAndUpdate(String field) throws Exception {
		UUID clinic = UUID.randomUUID();
		String token = token(clinic, "creator", "CLINICIAN", List.of());
		UUID id = create(token);
		String createBody = CREATE.substring(0, CREATE.length() - 1) + ",\"" + field + "\":\"forbidden\"}";
		assertThat(request("POST", "/patients", token, createBody).statusCode()).isEqualTo(400);
		String updateBody = createBody.substring(0, createBody.length() - 1) + ",\"version\":0}";
		assertThat(request("PUT", "/patients/" + id, token, updateBody).statusCode()).isEqualTo(400);
	}

	@Test
	void validatesBodylessDeleteAndOtherExternalInputs() throws Exception {
		String token = token(UUID.randomUUID(), "creator", "CLINICIAN", List.of());
		UUID id = create(token);
		assertThat(request("DELETE", "/patients/invalid", token, null).statusCode()).isEqualTo(400);
		assertThat(request("DELETE", "/patients/" + id + "?clinicId=forged", token, null).statusCode()).isEqualTo(400);
		assertThat(request("DELETE", "/patients/" + id, token, "{}").statusCode()).isEqualTo(400);
		assertThat(request("POST", "/patients", token, "{}").statusCode()).isEqualTo(400);
		assertThat(request("PUT", "/patients/" + id, token, CREATE).statusCode()).isEqualTo(400);
		assertThat(request("GET", "/patients/" + id, token, null).statusCode()).isEqualTo(200);
	}

	@ParameterizedTest
	@CsvSource({"exp,missing,AUTH_INVALID_EXP", "iat,missing,AUTH_INVALID_IAT", "nbf,missing,AUTH_INVALID_NBF",
			"exp,malformed,AUTH_INVALID_EXP", "iat,malformed,AUTH_INVALID_IAT", "nbf,malformed,AUTH_INVALID_NBF",
			"exp,past,AUTH_TOKEN_EXPIRED", "iat,future,AUTH_INVALID_IAT", "nbf,future,AUTH_TOKEN_NOT_ACTIVE"})
	void rejectsInvalidTemporalClaimsBeforeEndpointExecution(String claim, String kind, String code) throws Exception {
		Map<String, Object> claims = TestIdentity.claims(UUID.randomUUID(), "creator", "CLINICIAN", List.of());
		if (kind.equals("missing")) {
			claims.remove(claim);
		}
		else if (kind.equals("malformed")) {
			claims.put(claim, "invalid");
		}
		else {
			claims.put(claim, Instant.now().plusSeconds(kind.equals("past") ? -40 : 40).getEpochSecond());
		}
		HttpResponse<String> response = request("POST", "/patients", TestIdentity.token(claims), CREATE);
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.body()).contains(code).doesNotContain("synthetic@example.invalid");
	}

	@Test
	void rejectsMissingAuthenticationUnsignedAndUnexpectedAlgorithms() throws Exception {
		assertThat(request("POST", "/patients", null, CREATE).statusCode()).isEqualTo(401);
		assertThat(request("POST", "/patients", "eyJhbGciOiJub25lIn0.e30.", CREATE).statusCode()).isEqualTo(401);
		Map<String, Object> claims = TestIdentity.claims(UUID.randomUUID(), "creator", "CLINICIAN", List.of());
		com.nimbusds.jose.JWSObject token = new com.nimbusds.jose.JWSObject(
				new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS512), new com.nimbusds.jose.Payload(claims));
		token.sign(new com.nimbusds.jose.crypto.RSASSASigner(TestIdentity.KEYS.getPrivate()));
		assertThat(request("POST", "/patients", token.serialize(), CREATE).statusCode()).isEqualTo(401);
		claims.put("scope", "unrelated:read");
		HttpResponse<String> forbidden = request("POST", "/patients", TestIdentity.token(claims), CREATE);
		assertThat(forbidden.statusCode()).isEqualTo(403);
		assertThat(forbidden.body()).contains("AUTH_FORBIDDEN_INSUFFICIENT_SCOPE");
		assertThat(request("GET", "/not-an-endpoint", TestIdentity.token(claims), null).statusCode()).isEqualTo(403);
	}

	@Test
	void rejectsMissingIdentityClaimsAndUnassignedClinic() throws Exception {
		for (String claim : List.of("sub", "clinic_id", "clinic_ids", "role", "scope")) {
			Map<String, Object> claims = TestIdentity.claims(UUID.randomUUID(), "creator", "CLINICIAN", List.of());
			claims.remove(claim);
			assertThat(request("POST", "/patients", TestIdentity.token(claims), CREATE).statusCode()).isEqualTo(401);
		}
		Map<String, Object> claims = TestIdentity.claims(UUID.randomUUID(), "creator", "CLINICIAN", List.of());
		claims.put("clinic_ids", List.of(UUID.randomUUID().toString()));
		assertThat(request("POST", "/patients", TestIdentity.token(claims), CREATE).statusCode()).isEqualTo(401);
	}
}