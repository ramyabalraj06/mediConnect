package com.demo.mediconnect.patient;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Converter
public class PatientFieldEncryptionConverter implements AttributeConverter<String, String> {

	private static final int NONCE_LENGTH = 12;
	private static final SecureRandom RANDOM = new SecureRandom();
	private final SecretKeySpec key;

	public PatientFieldEncryptionConverter(
			@Value("${mediconnect.patient.encryption-key}") String encodedKey) {
		byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
		if (decodedKey.length != 32) {
			throw new IllegalArgumentException("Patient encryption key must be a Base64-encoded 256-bit key");
		}
		this.key = new SecretKeySpec(decodedKey, "AES");
	}

	@Override
	public String convertToDatabaseColumn(String value) {
		if (value == null) {
			return null;
		}
		try {
			byte[] nonce = new byte[NONCE_LENGTH];
			RANDOM.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
			byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
					.put(nonce).put(encrypted).array());
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Unable to encrypt patient field");
		}
	}

	@Override
	public String convertToEntityAttribute(String value) {
		if (value == null) {
			return null;
		}
		try {
			ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value));
			if (buffer.remaining() < NONCE_LENGTH + 16) {
				throw new IllegalArgumentException("Invalid encrypted patient field");
			}
			byte[] nonce = new byte[NONCE_LENGTH];
			buffer.get(nonce);
			byte[] encrypted = new byte[buffer.remaining()];
			buffer.get(encrypted);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw new IllegalStateException("Unable to decrypt patient field");
		}
	}
}