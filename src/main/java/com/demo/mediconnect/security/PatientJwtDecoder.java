package com.demo.mediconnect.security;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public final class PatientJwtDecoder implements JwtDecoder {

	private final RSAPublicKey publicKey;
	private final String issuer;
	private final String audience;
	private final Clock clock;
	private final long skewSeconds;

	public PatientJwtDecoder(RSAPublicKey publicKey, String issuer, String audience,
			Clock clock, long skewSeconds) {
		if (publicKey.getModulus().bitLength() < 2048 || issuer.isBlank() || audience.isBlank()
				|| skewSeconds < 0 || skewSeconds > 60) {
			throw new IllegalArgumentException("Invalid JWT verifier configuration");
		}
		this.publicKey = publicKey;
		this.issuer = issuer;
		this.audience = audience;
		this.clock = clock;
		this.skewSeconds = skewSeconds;
	}

	@Override
	public Jwt decode(String token) {
		try {
			if (token.length() > 16384) {
				throw invalid("AUTH_INVALID_TOKEN");
			}
			SignedJWT signed = SignedJWT.parse(token);
			if (!JWSAlgorithm.RS256.equals(signed.getHeader().getAlgorithm())
					|| signed.getHeader().getCriticalParams() != null
					|| !signed.verify(new RSASSAVerifier(publicKey))) {
				throw invalid("AUTH_INVALID_TOKEN");
			}
			Map<String, Object> claims = new HashMap<>(signed.getPayload().toJSONObject());
			Instant expires = timestamp(claims.get("exp"), "AUTH_INVALID_EXP");
			Instant issued = timestamp(claims.get("iat"), "AUTH_INVALID_IAT");
			Instant notBefore = timestamp(claims.get("nbf"), "AUTH_INVALID_NBF");
			Instant now = clock.instant();
			if (!expires.isAfter(issued)) {
				throw invalid("AUTH_INVALID_IAT");
			}
			if (issued.isAfter(now.plusSeconds(skewSeconds))) {
				throw invalid("AUTH_INVALID_IAT");
			}
			if (!notBefore.isBefore(expires) || notBefore.isBefore(issued)) {
				throw invalid("AUTH_INVALID_NBF");
			}
			if (!expires.isAfter(now.minusSeconds(skewSeconds))) {
				throw invalid("AUTH_TOKEN_EXPIRED");
			}
			if (notBefore.isAfter(now.plusSeconds(skewSeconds))) {
				throw invalid("AUTH_TOKEN_NOT_ACTIVE");
			}
			Object audiences = claims.get("aud");
			if (!issuer.equals(claims.get("iss")) || !(audience.equals(audiences)
					|| audiences instanceof List<?> list && list.contains(audience))) {
				throw invalid("AUTH_INVALID_TOKEN");
			}
			if (!(claims.get("sub") instanceof String subject)
					|| !subject.matches("[A-Za-z0-9:_-]{1,128}")) {
				throw invalid("AUTH_INVALID_TOKEN");
			}
			UUID clinic = uuid(claims.get("clinic_id"));
			List<String> assignedClinics = identifiers(claims.get("clinic_ids"), 100);
			if (!assignedClinics.contains(clinic.toString())
					|| !(claims.get("role") instanceof String role)
					|| !Set.of("PATIENT", "CLINICIAN", "ADMIN").contains(role)
					|| !(claims.get("scope") instanceof String scope)
					|| scope.isBlank() || scope.length() > 512
					|| !scope.matches("[A-Za-z0-9:_ -]+")) {
				throw invalid("AUTH_INVALID_TOKEN");
			}
			claims.put("patient_ids", identifiers(claims.getOrDefault("patient_ids", List.of()), 500));
			claims.put("exp", expires);
			claims.put("iat", issued);
			claims.put("nbf", notBefore);
			return Jwt.withTokenValue(token).headers(headers -> headers.putAll(signed.getHeader().toJSONObject()))
					.claims(values -> values.putAll(claims)).build();
		}
		catch (BadJwtException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw invalid("AUTH_INVALID_TOKEN");
		}
	}

	private static Instant timestamp(Object value, String code) {
		if (!(value instanceof Long || value instanceof Integer) || ((Number) value).longValue() < 0) {
			throw invalid(code);
		}
		try {
			return Instant.ofEpochSecond(((Number) value).longValue());
		}
		catch (RuntimeException exception) {
			throw invalid(code);
		}
	}

	private static UUID uuid(Object value) {
		if (!(value instanceof String text)) {
			throw invalid("AUTH_INVALID_TOKEN");
		}
		UUID id = UUID.fromString(text);
		if (!id.toString().equals(text)) {
			throw invalid("AUTH_INVALID_TOKEN");
		}
		return id;
	}

	private static List<String> identifiers(Object value, int maximum) {
		if (!(value instanceof List<?> list) || list.size() > maximum) {
			throw invalid("AUTH_INVALID_TOKEN");
		}
		return list.stream().map(item -> uuid(item).toString()).toList();
	}

	private static BadJwtException invalid(String code) {
		return new BadJwtException(code);
	}
}