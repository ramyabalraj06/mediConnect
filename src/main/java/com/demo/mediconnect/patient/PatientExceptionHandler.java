package com.demo.mediconnect.patient;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class PatientExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(PatientExceptionHandler.class);

	public record ErrorResponse(String code) {
	}

	@ExceptionHandler(PatientNotFoundException.class)
	public ResponseEntity<ErrorResponse> notFound() {
		return error(404, "PATIENT_NOT_FOUND");
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
			HttpMessageNotReadableException.class, HandlerMethodValidationException.class,
			IllegalArgumentException.class})
	public ResponseEntity<ErrorResponse> invalidInput() {
		return error(400, "VALIDATION_ERROR");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> forbidden(AccessDeniedException exception) {
		return error(403, "AUTH_FORBIDDEN_INSUFFICIENT_ROLE".equals(exception.getMessage())
				? "AUTH_FORBIDDEN_INSUFFICIENT_ROLE" : "AUTH_FORBIDDEN_INSUFFICIENT_SCOPE");
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorResponse> unauthenticated() {
		return error(401, "AUTH_INVALID_TOKEN");
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ErrorResponse> conflict() {
		return error(409, "PATIENT_VERSION_CONFLICT");
	}

	private ResponseEntity<ErrorResponse> error(int status, String code) {
		LOGGER.atWarn().addKeyValue("code", code).log("Patient request rejected");
		return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ErrorResponse(code));
	}
}