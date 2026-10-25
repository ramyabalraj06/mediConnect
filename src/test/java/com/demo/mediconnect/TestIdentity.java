package com.demo.mediconnect;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class TestIdentity {

	public static final KeyPair KEYS = keys();

	private TestIdentity() {
	}

	private static KeyPair keys() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	public static void configure(DynamicPropertyRegistry registry) {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		String encodedKey = Base64.getEncoder().encodeToString(key);
		String database = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
		registry.add("mediconnect.patient.encryption-key", () -> encodedKey);
		registry.add("spring.datasource.url", () -> database);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("mediconnect.security.public-key", () -> Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded()));
		registry.add("mediconnect.security.issuer", () -> "test-issuer");
		registry.add("mediconnect.security.audience", () -> "mediconnect");
	}

	public static Map<String, Object> claims(UUID clinic, String subject, String role, List<String> patientIds) {
		Instant now = Instant.now();
		return new java.util.HashMap<>(Map.ofEntries(
				Map.entry("iss", "test-issuer"), Map.entry("aud", "mediconnect"),
				Map.entry("sub", subject), Map.entry("role", role),
				Map.entry("scope", "patients:create patients:read patients:write"),
				Map.entry("clinic_id", clinic.toString()), Map.entry("clinic_ids", List.of(clinic.toString())),
				Map.entry("patient_ids", patientIds),
				Map.entry("iat", now.minusSeconds(60).getEpochSecond()),
				Map.entry("nbf", now.minusSeconds(60).getEpochSecond()),
				Map.entry("exp", now.plusSeconds(600).getEpochSecond())));
	}

	public static void authenticate(UUID clinic, String subject) {
		Map<String, Object> claims = claims(clinic, subject, "CLINICIAN", List.of());
		claims.put("iat", Instant.now().minusSeconds(60));
		claims.put("nbf", Instant.now().minusSeconds(60));
		claims.put("exp", Instant.now().plusSeconds(600));
		Jwt jwt = Jwt.withTokenValue("synthetic-test-token").header("alg", "RS256")
				.claims(values -> values.putAll(claims)).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationConverter().convert(jwt));
	}

	public static String token(Map<String, Object> claims) throws Exception {
		JWSObject token = new JWSObject(new JWSHeader(JWSAlgorithm.RS256), new Payload(claims));
		token.sign(new RSASSASigner(KEYS.getPrivate()));
		return token.serialize();
	}
}