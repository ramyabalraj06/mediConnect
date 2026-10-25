package com.demo.mediconnect.patient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;

import com.demo.mediconnect.TestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.startsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
class PatientControllerTests {

	private static final String PATH = "/api/patients";
	private static final String BODY = "{\"firstName\":\"Test\",\"lastName\":\"Patient\",\"email\":\"synthetic@example.invalid\"}";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private FilterChainProxy securityFilterChain;

	private MockMvc mvc;
	private String bearer;
	private UUID clinicId;
	private Logger rootLogger;
	private ListAppender<ILoggingEvent> logAppender;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestIdentity.configure(registry);
	}

	@BeforeEach
	void setUp() throws Exception {
		rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		logAppender = new ListAppender<>();
		logAppender.start();
		rootLogger.addAppender(logAppender);
		mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
		clinicId = UUID.randomUUID();
		bearer = "Bearer " + TestIdentity.token(TestIdentity.claims(clinicId, "creator", "CLINICIAN", List.of()));
	}

	@AfterEach
	void detachLogCapture() {
		rootLogger.detachAppender(logAppender);
		logAppender.stop();
	}

	@Test
	void createReadUpdateAndSoftDelete() throws Exception {
		String response = mvc.perform(post(PATH).header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", startsWith(PATH + "/")))
				.andExpect(jsonPath("$.clinicId").doesNotExist())
				.andExpect(jsonPath("$.createdAt").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String id = JsonMapper.builder().build().readTree(response).get("id").asString();
		assertResponseFields(response);
		String path = PATH + "/" + id;
		String readResponse = mvc.perform(get(path).header("Authorization", bearer))
				.andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Test"))
				.andReturn().getResponse().getContentAsString();
		assertResponseFields(readResponse);
		String updateResponse = mvc.perform(put(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
				.content(BODY.substring(0, BODY.length() - 1) + ",\"version\":0}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
				.andReturn().getResponse().getContentAsString();
		assertResponseFields(updateResponse);
		mvc.perform(delete(path).header("Authorization", bearer)).andExpect(status().isNoContent())
				.andExpect(content().string(""));
		mvc.perform(delete(path).header("Authorization", bearer)).andExpect(status().isNoContent())
				.andExpect(content().string(""));
		mvc.perform(get(path).header("Authorization", bearer)).andExpect(status().isNotFound())
				.andExpect(content().json("{\"code\":\"PATIENT_NOT_FOUND\"}"));

		assertTimedLogs(List.of("create", "getById", "update", "delete", "delete", "getById"),
				201, 200, 200, 204, 204, null);
		assertNoSensitiveLogs(id);
	}

	@Test
	void invalidAndForbiddenFieldsReturn400() throws Exception {
		mvc.perform(post(PATH).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(post(PATH).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
				.content(BODY.substring(0, BODY.length() - 1) + ",\"clinic_id\":\"forbidden\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(delete(PATH + "/invalid").header("Authorization", bearer))
				.andExpect(status().isBadRequest());
		mvc.perform(post(PATH).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"SensitiveExample\",\"lastName\":\"SyntheticOnly\",\"email\":\"invalid-sensitive-email\"}"))
				.andExpect(status().isBadRequest());
		assertNoSensitiveLogs("SensitiveExample", "SyntheticOnly", "invalid-sensitive-email");
	}

	@Test
	void missingAuthenticationReturns401() throws Exception {
		mvc.perform(get(PATH + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
		assertThat(controllerLogs()).isEmpty();
	}

	@Test
	void methodSecurityRejectsMissingScope() throws Exception {
		Map<String, Object> claims = TestIdentity.claims(clinicId, "creator", "CLINICIAN", List.of());
		claims.put("scope", "patients:read");
		mvc.perform(post(PATH).header("Authorization", "Bearer " + TestIdentity.token(claims))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN_INSUFFICIENT_SCOPE"));
		assertThat(controllerLogs()).isEmpty();
	}

	@Test
	void absentPatientReturns404() throws Exception {
		String id = UUID.randomUUID().toString();
		mvc.perform(get(PATH + "/" + id).header("Authorization", bearer))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
		assertTimedLogs(List.of("getById"), (Integer) null);
		assertNoSensitiveLogs(id);
	}

	@Test
	void rejectedDeleteBodyStillLogsStartAndEndWithoutBodyData() throws Exception {
		String id = UUID.randomUUID().toString();
		mvc.perform(delete(PATH + "/" + id).header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"SensitiveDeleteExample\"}"))
				.andExpect(status().isBadRequest());
		assertTimedLogs(List.of("delete"), (Integer) null);
		assertNoSensitiveLogs(id, "SensitiveDeleteExample");
	}

	private List<ILoggingEvent> controllerLogs() {
		return logAppender.list.stream()
				.filter(event -> event.getLoggerName().equals(PatientController.class.getName())).toList();
	}

	private void assertTimedLogs(List<String> operations, Integer... statuses) {
		List<ILoggingEvent> events = controllerLogs();
		assertThat(statuses).hasSize(operations.size());
		assertThat(events).hasSize(operations.size() * 2);
		assertThat(events).allSatisfy(event -> {
			assertThat(event.getArgumentArray()).isNullOrEmpty();
			assertThat(event.getThrowableProxy()).isNull();
		});
		for (int index = 0; index < operations.size(); index++) {
			ILoggingEvent start = events.get(index * 2);
			ILoggingEvent end = events.get(index * 2 + 1);
			assertThat(start.getFormattedMessage()).isEqualTo("Patient API call started");
			assertThat(end.getFormattedMessage()).isEqualTo("Patient API call ended");
			assertThat(start.getKeyValuePairs()).extracting(pair -> pair.key)
					.containsExactly("operation", "startedAt");
			List<String> endKeys = end.getKeyValuePairs().stream().map(pair -> pair.key).toList();
			assertThat(endKeys).containsExactlyElementsOf(statuses[index] == null
					? List.of("operation", "startedAt", "endedAt", "durationMs", "outcome")
					: List.of("operation", "startedAt", "endedAt", "durationMs", "outcome", "status"));
			assertThat(start.getKeyValuePairs().get(0).value).isEqualTo(operations.get(index));
			assertThat(end.getKeyValuePairs().get(0).value).isEqualTo(operations.get(index));
			assertThat(start.getKeyValuePairs().get(1).value).isInstanceOf(Instant.class);
			assertThat(end.getKeyValuePairs().get(1).value).isEqualTo(start.getKeyValuePairs().get(1).value);
			assertThat(end.getKeyValuePairs().get(2).value).isInstanceOf(Instant.class);
			assertThat((Long) end.getKeyValuePairs().get(3).value).isGreaterThanOrEqualTo(0L);
			assertThat(end.getKeyValuePairs().get(4).value)
					.isEqualTo(statuses[index] == null ? "failure" : "success");
			if (statuses[index] != null) {
				assertThat(end.getKeyValuePairs().get(5).value).isEqualTo(statuses[index]);
			}
		}
	}

	private static void assertResponseFields(String response) {
		Map<?, ?> fields = JsonMapper.builder().build().readValue(response, Map.class);
		assertThat(fields.keySet()).extracting(Object::toString)
				.containsExactlyInAnyOrder("id", "firstName", "lastName", "email", "version");
	}

	private void assertNoSensitiveLogs(String... sensitiveValues) {
		String logs = logAppender.list.stream().map(event -> event.getFormattedMessage()
				+ java.util.Arrays.toString(event.getArgumentArray()) + event.getKeyValuePairs()
				+ event.getMDCPropertyMap() + (event.getThrowableProxy() == null ? ""
						: ThrowableProxyUtil.asString(event.getThrowableProxy())))
				.collect(java.util.stream.Collectors.joining("\n"));
		assertThat(logs).doesNotContain("synthetic@example.invalid", clinicId.toString(), bearer,
				bearer.substring("Bearer ".length()), BODY);
		assertThat(logs).doesNotContain(sensitiveValues);
	}
}