package com.demo.mediconnect;

import java.util.Base64;
import java.util.UUID;

import com.demo.mediconnect.patient.Patient;
import com.demo.mediconnect.patient.PatientFieldEncryptionConverter;
import com.demo.mediconnect.patient.PatientNotFoundException;
import com.demo.mediconnect.patient.PatientRequest;
import com.demo.mediconnect.patient.PatientRepository;
import com.demo.mediconnect.patient.PatientAuditRepository;
import org.junit.jupiter.api.AfterEach;
import com.demo.mediconnect.patient.PatientService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MediconnectApplicationTests {

	@Autowired
	private PatientService patientService;

	@Autowired
	private PatientFieldEncryptionConverter encryptionConverter;

	@Autowired
	private PatientRepository patientRepository;

	@Autowired
	private PatientAuditRepository auditRepository;

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		TestIdentity.configure(registry);
	}

	@AfterEach
	void clearIdentity() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void contextLoads() {
	}

	@Test
	void patientLifecyclePersistsAcrossServiceTransactions() {
		UUID clinicId = UUID.randomUUID();
		TestIdentity.authenticate(clinicId, "synthetic-clinician");
		Patient created = patientService.create(clinicId,
				new PatientRequest("Test", "Patient", "test.patient@example.invalid"));
		assertThat(created.getId()).isNotNull();
		Patient loaded = patientService.getById(clinicId, created.getId());
		assertThat(loaded.getFirstName()).isEqualTo("Test");
		assertThat(loaded.getLastName()).isEqualTo("Patient");
		assertThat(loaded.getEmail()).isEqualTo("test.patient@example.invalid");

		patientService.update(clinicId, created.getId(),
				new PatientRequest("Updated", "Example", "updated@example.invalid"), created.getVersion());
		Patient updated = patientService.getById(clinicId, created.getId());
		assertThat(updated.getFirstName()).isEqualTo("Updated");
		assertThat(updated.getLastName()).isEqualTo("Example");
		assertThat(updated.getEmail()).isEqualTo("updated@example.invalid");
		assertThat(updated.getClinicId()).isEqualTo(clinicId);

		patientService.delete(clinicId, created.getId());
		patientService.delete(clinicId, created.getId());
		Patient retained = patientRepository.findByIdAndClinicId(created.getId(), clinicId).orElseThrow();
		assertThat(retained.getDeletedAt()).isNotNull();
		assertThat(retained.getVersion()).isGreaterThan(created.getVersion());
		assertThat(auditRepository.findByPatientIdAndClinicIdOrderByOccurredAtAsc(created.getId(), clinicId))
				.extracting(event -> event.getAction()).containsExactly("CREATE", "READ", "UPDATE", "READ", "DEACTIVATE");
		assertThatThrownBy(() -> patientService.getById(clinicId, created.getId()))
				.isInstanceOf(PatientNotFoundException.class);
		assertThatThrownBy(() -> patientService.update(clinicId, created.getId(),
				new PatientRequest("Test", "Patient", "test@example.invalid"), created.getVersion()))
				.isInstanceOf(PatientNotFoundException.class);
	}

	@Test
	void anotherClinicCannotReadUpdateOrDeletePatient() {
		UUID ownerClinic = UUID.randomUUID();
		UUID otherClinic = UUID.randomUUID();
		TestIdentity.authenticate(ownerClinic, "synthetic-clinician");
		PatientRequest request = new PatientRequest("Test", "Patient", "test@example.invalid");
		Patient patient = patientService.create(ownerClinic, request);

		assertThatThrownBy(() -> patientService.getById(otherClinic, patient.getId()))
				.isInstanceOf(PatientNotFoundException.class);
		assertThatThrownBy(() -> patientService.update(otherClinic, patient.getId(), request, patient.getVersion()))
				.isInstanceOf(PatientNotFoundException.class);
		assertThatThrownBy(() -> patientService.delete(otherClinic, patient.getId()))
				.isInstanceOf(PatientNotFoundException.class);
		assertThat(patientService.getById(ownerClinic, patient.getId()).getFirstName()).isEqualTo("Test");
	}

	@Test
	void missingPatientCannotBeUpdatedOrDeleted() {
		UUID clinicId = UUID.randomUUID();
		TestIdentity.authenticate(clinicId, "synthetic-clinician");
		UUID patientId = UUID.randomUUID();
		PatientRequest request = new PatientRequest("Test", "Patient", "test@example.invalid");
		assertThatThrownBy(() -> patientService.update(clinicId, patientId, request, 0L))
				.isInstanceOf(PatientNotFoundException.class);
		assertThatThrownBy(() -> patientService.delete(clinicId, patientId))
				.isInstanceOf(PatientNotFoundException.class);
	}

	@Test
	void staleDetachedWritesAreRejectedByJpaVersioning() {
		UUID clinicId = UUID.randomUUID();
		TestIdentity.authenticate(clinicId, "synthetic-clinician");
		PatientRequest request = new PatientRequest("Test", "Patient", "test@example.invalid");
		Patient patient = patientService.create(clinicId, request);
		Patient stale = patientRepository.findByIdAndClinicId(patient.getId(), clinicId).orElseThrow();
		patientService.update(clinicId, patient.getId(), request, patient.getVersion());
		assertThatThrownBy(() -> patientRepository.save(stale))
				.isInstanceOf(OptimisticLockingFailureException.class);
	}

	@Test
	void serviceCannotBeCalledWithoutAuthentication() {
		assertThatThrownBy(() -> patientService.create(UUID.randomUUID(),
				new PatientRequest("Test", "Patient", "test@example.invalid")))
				.isInstanceOf(AuthenticationCredentialsNotFoundException.class);
	}

	@Test
	void invalidPatientInputIsRejected() {
		UUID clinicId = UUID.randomUUID();
		PatientRequest invalid = new PatientRequest("", "Patient", "not-an-email");
		assertThatThrownBy(() -> patientService.create(clinicId, invalid))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> patientService.update(clinicId, UUID.randomUUID(), invalid, 0L))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> patientService.getById(null, UUID.randomUUID()))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> patientService.create(clinicId, null))
				.isInstanceOf(ConstraintViolationException.class);
	}

	@Test
	void encryptionUsesUniqueNoncesAndRejectsTampering() {
		String value = "synthetic@example.invalid";
		String encrypted = encryptionConverter.convertToDatabaseColumn(value);
		assertThat(encrypted).doesNotContain(value);
		assertThat(encryptionConverter.convertToDatabaseColumn(value)).isNotEqualTo(encrypted);
		assertThat(encryptionConverter.convertToEntityAttribute(encrypted)).isEqualTo(value);
		byte[] tampered = Base64.getDecoder().decode(encrypted);
		tampered[tampered.length - 1] ^= 1;
		assertThatThrownBy(() -> encryptionConverter.convertToEntityAttribute(
				Base64.getEncoder().encodeToString(tampered))).isInstanceOf(IllegalStateException.class);
		assertThat(encryptionConverter.convertToDatabaseColumn(null)).isNull();
		assertThat(encryptionConverter.convertToEntityAttribute(null)).isNull();
	}
}
