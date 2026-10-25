package com.demo.mediconnect.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jwt.BadJwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientJwtDecoderTests {

	static final KeyPair KEYS = keys();
	static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
	static final UUID CLINIC = UUID.randomUUID();
	private final PatientJwtDecoder decoder = new PatientJwtDecoder((RSAPublicKey) KEYS.getPublic(),
			"test-issuer", "mediconnect", Clock.fixed(NOW, ZoneOffset.UTC), 30);

	static KeyPair keys() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	static Map<String, Object> claims() {
		Map<String, Object> claims = new HashMap<>();
		claims.put("iss", "test-issuer");
		claims.put("aud", "mediconnect");
		claims.put("sub", "synthetic-clinician");
		claims.put("clinic_id", CLINIC.toString());
		claims.put("clinic_ids", List.of(CLINIC.toString()));
		claims.put("role", "CLINICIAN");
		claims.put("scope", "patients:create patients:read patients:write");
		claims.put("iat", NOW.minusSeconds(60).getEpochSecond());
		claims.put("nbf", NOW.minusSeconds(60).getEpochSecond());
		claims.put("exp", NOW.plusSeconds(300).getEpochSecond());
		return claims;
	}

	static String token(Map<String, Object> claims, JWSAlgorithm algorithm, KeyPair keys) throws Exception {
		JWSObject signed = new JWSObject(new JWSHeader(algorithm), new Payload(claims));
		signed.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
		return signed.serialize();
	}

	@Test
	void acceptsVerifiedRs256AndBoundedSkew() throws Exception {
		assertThat(decoder.decode(token(claims(), JWSAlgorithm.RS256, KEYS)).getSubject())
				.isEqualTo("synthetic-clinician");
		Map<String, Object> claims = claims();
		claims.put("iat", NOW.plusSeconds(30).getEpochSecond());
		claims.put("nbf", NOW.plusSeconds(30).getEpochSecond());
		assertThat(decoder.decode(token(claims, JWSAlgorithm.RS256, KEYS))).isNotNull();
	}

	@Test
	void rejectsNoneUnexpectedAlgorithmWrongKeyAndKeyConfusion() throws Exception {
		String none = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				"{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".e30.";
		assertThatThrownBy(() -> decoder.decode(none)).hasMessage("AUTH_INVALID_TOKEN");
		assertThatThrownBy(() -> decoder.decode(token(claims(), JWSAlgorithm.RS512, KEYS)))
				.hasMessage("AUTH_INVALID_TOKEN");
		assertThatThrownBy(() -> decoder.decode(token(claims(), JWSAlgorithm.RS256, keys())))
				.hasMessage("AUTH_INVALID_TOKEN");
		JWSObject confused = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(claims()));
		confused.sign(new MACSigner(KEYS.getPublic().getEncoded()));
		assertThatThrownBy(() -> decoder.decode(confused.serialize())).hasMessage("AUTH_INVALID_TOKEN");
	}

	static Stream<Arguments> invalidClaims() {
		return Stream.of(
				Arguments.of("exp", null, "AUTH_INVALID_EXP"),
				Arguments.of("exp", "tomorrow", "AUTH_INVALID_EXP"),
				Arguments.of("exp", NOW.minusSeconds(31).getEpochSecond(), "AUTH_TOKEN_EXPIRED"),
				Arguments.of("iat", null, "AUTH_INVALID_IAT"),
				Arguments.of("iat", "invalid", "AUTH_INVALID_IAT"),
				Arguments.of("iat", NOW.plusSeconds(31).getEpochSecond(), "AUTH_INVALID_IAT"),
				Arguments.of("nbf", null, "AUTH_INVALID_NBF"),
				Arguments.of("nbf", true, "AUTH_INVALID_NBF"),
				Arguments.of("nbf", NOW.plusSeconds(31).getEpochSecond(), "AUTH_TOKEN_NOT_ACTIVE"),
				Arguments.of("nbf", NOW.plusSeconds(301).getEpochSecond(), "AUTH_INVALID_NBF"),
				Arguments.of("iss", "untrusted", "AUTH_INVALID_TOKEN"),
				Arguments.of("aud", "other-service", "AUTH_INVALID_TOKEN"),
				Arguments.of("clinic_id", UUID.randomUUID().toString(), "AUTH_INVALID_TOKEN"),
				Arguments.of("clinic_ids", List.of(), "AUTH_INVALID_TOKEN"),
				Arguments.of("role", null, "AUTH_INVALID_TOKEN"),
				Arguments.of("role", "UNKNOWN", "AUTH_INVALID_TOKEN"),
				Arguments.of("scope", null, "AUTH_INVALID_TOKEN"),
				Arguments.of("patient_ids", List.of("invalid"), "AUTH_INVALID_TOKEN"));
	}

	@ParameterizedTest
	@MethodSource("invalidClaims")
	void rejectsInvalidClaims(String claim, Object value, String code) throws Exception {
		Map<String, Object> claims = claims();
		if (value == null) {
			claims.remove(claim);
		}
		else {
			claims.put(claim, value);
		}
		assertThatThrownBy(() -> decoder.decode(token(claims, JWSAlgorithm.RS256, KEYS)))
				.isInstanceOf(BadJwtException.class).hasMessage(code);
	}
}