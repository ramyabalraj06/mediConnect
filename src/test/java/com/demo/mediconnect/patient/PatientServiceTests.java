package com.demo.mediconnect.patient;

import java.util.Optional;
import java.util.UUID;

import com.demo.mediconnect.security.PatientAccess;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class PatientServiceTests {

	@Mock
	private PatientRepository patientRepository;

	@Mock
	private PatientAccess access;

	@Mock
	private PatientAuditRepository auditRepository;

	@InjectMocks
	private PatientService patientService;

	private final UUID clinicId = UUID.randomUUID();
	private final UUID patientId = UUID.randomUUID();
	private final PatientRequest request = new PatientRequest("Test", "Patient", "test@example.invalid");

	@BeforeEach
	void actor() {
		lenient().when(access.requireOperation(any(), anyString(), anyBoolean())).thenReturn("synthetic-clinician");
	}

	@Test
	void createSavesOnlyWhitelistedPatientFields() {
		when(patientRepository.save(any(Patient.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		Patient patient = patientService.create(clinicId, request);

		assertThat(patient.getClinicId()).isEqualTo(clinicId);
		assertThat(patient.getFirstName()).isEqualTo(request.firstName());
		assertThat(patient.getLastName()).isEqualTo(request.lastName());
		assertThat(patient.getEmail()).isEqualTo(request.email());
		verify(patientRepository).save(patient);
	}

	@Test
	void getByIdUsesClinicScopedLookup() {
		Patient patient = new Patient(clinicId, request, "synthetic-clinician");
		when(patientRepository.findByIdAndClinicIdAndDeletedAtIsNull(patientId, clinicId)).thenReturn(Optional.of(patient));

		assertThat(patientService.getById(clinicId, patientId)).isSameAs(patient);
	}

	@Test
	void updatePreservesClinicAndSavesChanges() {
		Patient patient = new Patient(clinicId, request, "synthetic-clinician");
		PatientRequest updated = new PatientRequest("Updated", "Example", "updated@example.invalid");
		ReflectionTestUtils.setField(patient, "version", 0L);
		when(patientRepository.findByIdAndClinicIdAndDeletedAtIsNull(patientId, clinicId)).thenReturn(Optional.of(patient));
		when(patientRepository.save(patient)).thenReturn(patient);

		assertThat(patientService.update(clinicId, patientId, updated, 0L)).isSameAs(patient);
		assertThat(patient.getClinicId()).isEqualTo(clinicId);
		assertThat(patient.getFirstName()).isEqualTo(updated.firstName());
		assertThat(patient.getLastName()).isEqualTo(updated.lastName());
		assertThat(patient.getEmail()).isEqualTo(updated.email());
		verify(patientRepository).save(patient);
	}

	@Test
	void deleteDeactivatesPatientAndIsIdempotent() {
		Patient patient = new Patient(clinicId, request, "synthetic-clinician");
		when(patientRepository.findByIdAndClinicId(patientId, clinicId)).thenReturn(Optional.of(patient));

		patientService.delete(clinicId, patientId);
		patientService.delete(clinicId, patientId);

		assertThat(patient.getDeletedAt()).isNotNull();
		verify(patientRepository).save(patient);
	}

	@Test
	void missingPatientCannotBeReadUpdatedOrDeleted() {
		when(patientRepository.findByIdAndClinicId(patientId, clinicId)).thenReturn(Optional.empty());
		when(patientRepository.findByIdAndClinicIdAndDeletedAtIsNull(patientId, clinicId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> patientService.getById(clinicId, patientId))
				.isInstanceOf(PatientNotFoundException.class).hasMessage("Patient not found");
		assertThatThrownBy(() -> patientService.update(clinicId, patientId, request, 0L))
				.isInstanceOf(PatientNotFoundException.class);
		assertThatThrownBy(() -> patientService.delete(clinicId, patientId))
				.isInstanceOf(PatientNotFoundException.class);
		verify(patientRepository, never()).save(any(Patient.class));
	}
}