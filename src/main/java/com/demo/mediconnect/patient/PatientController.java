package com.demo.mediconnect.patient;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.demo.mediconnect.security.PatientAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
@Validated
public class PatientController {

	private static final Logger LOGGER = LoggerFactory.getLogger(PatientController.class);
	private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
	private final PatientService service;
	private final PatientAccess access;

	public PatientController(PatientService service, PatientAccess access) {
		this.service = service;
		this.access = access;
	}

	@PostMapping
	@PreAuthorize("hasAuthority('SCOPE_patients:create')")
	public ResponseEntity<PatientResponse> create(@RequestBody @Valid PatientCreateRequest body,
			HttpServletRequest request) {
		return timed("create", () -> {
			rejectQuery(request);
			Patient patient = service.create(access.currentClinicId(), PatientMapper.toCommand(body));
			PatientResponse response = PatientMapper.toResponse(patient);
			return ResponseEntity.created(URI.create("/api/patients/" + patient.getId()))
					.cacheControl(CacheControl.noStore()).body(response);
		});
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('SCOPE_patients:read')")
	public ResponseEntity<PatientResponse> getById(@PathVariable @Pattern(regexp = UUID_PATTERN) String id,
			HttpServletRequest request) {
		return timed("getById", () -> {
			rejectQuery(request);
			PatientResponse response = PatientMapper.toResponse(
					service.getById(access.currentClinicId(), UUID.fromString(id)));
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
		});
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('SCOPE_patients:write')")
	public ResponseEntity<PatientResponse> update(@PathVariable @Pattern(regexp = UUID_PATTERN) String id,
			@RequestBody @Valid PatientUpdateRequest body, HttpServletRequest request) {
		return timed("update", () -> {
			rejectQuery(request);
			PatientResponse response = PatientMapper.toResponse(service.update(access.currentClinicId(), UUID.fromString(id),
					PatientMapper.toCommand(body), body.version()));
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
		});
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('SCOPE_patients:write')")
	public ResponseEntity<Void> delete(@PathVariable @Pattern(regexp = UUID_PATTERN) String id,
			@RequestBody(required = false) String body, HttpServletRequest request) {
		return timed("delete", () -> {
			rejectQuery(request);
			if (body != null) {
				throw new IllegalArgumentException("Delete must not contain a body");
			}
			service.delete(access.currentClinicId(), UUID.fromString(id));
			return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
		});
	}

	private static <T> ResponseEntity<T> timed(String operation, Supplier<ResponseEntity<T>> action) {
		Instant startedAt = Instant.now();
		long startedNanos = System.nanoTime();
		Integer status = null;
		LOGGER.atInfo().addKeyValue("operation", operation).addKeyValue("startedAt", startedAt)
				.log("Patient API call started");
		try {
			ResponseEntity<T> response = action.get();
			status = response.getStatusCode().value();
			return response;
		}
		finally {
			long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
			var event = LOGGER.atInfo().addKeyValue("operation", operation)
					.addKeyValue("startedAt", startedAt).addKeyValue("endedAt", Instant.now())
					.addKeyValue("durationMs", durationMs)
					.addKeyValue("outcome", status == null ? "failure" : "success");
			if (status != null) {
				event.addKeyValue("status", status);
			}
			event.log("Patient API call ended");
		}
	}

	private static void rejectQuery(HttpServletRequest request) {
		if (!request.getParameterMap().isEmpty()) {
			throw new IllegalArgumentException("Query parameters are not supported");
		}
	}
}