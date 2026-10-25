package com.demo.mediconnect.security;

import java.util.List;
import java.util.UUID;

import com.demo.mediconnect.patient.Patient;
import com.demo.mediconnect.patient.PatientNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class PatientAccess {

	public UUID currentClinicId() {
		return UUID.fromString(currentJwt().getClaimAsString("clinic_id"));
	}

	public String requireOperation(UUID clinicId, String scope, boolean staffOnly) {
		Jwt jwt = currentJwt();
		if (staffOnly && !List.of("CLINICIAN", "ADMIN").contains(jwt.getClaimAsString("role"))) {
			throw new AccessDeniedException("AUTH_FORBIDDEN_INSUFFICIENT_ROLE");
		}
		if (!List.of(jwt.getClaimAsString("scope").split(" +")).contains(scope)) {
			throw new AccessDeniedException("AUTH_FORBIDDEN_INSUFFICIENT_SCOPE");
		}
		if (!currentClinicId().equals(clinicId)) {
			throw new PatientNotFoundException();
		}
		return jwt.getSubject();
	}

	public void requireResource(Patient patient) {
		Jwt jwt = currentJwt();
		List<String> grants = jwt.getClaimAsStringList("patient_ids");
		boolean creator = !"PATIENT".equals(jwt.getClaimAsString("role"))
				&& jwt.getSubject().equals(patient.getCreatedBy());
		if (!currentClinicId().equals(patient.getClinicId())
				|| !(creator || grants != null && grants.contains(patient.getId().toString()))) {
			throw new PatientNotFoundException();
		}
	}

	private Jwt currentJwt() {
		if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)
				|| !authentication.isAuthenticated()) {
			throw new AuthenticationCredentialsNotFoundException("AUTH_INVALID_TOKEN");
		}
		return authentication.getToken();
	}
}