package com.demo.mediconnect.patient;

import java.util.UUID;
import java.util.Objects;

import com.demo.mediconnect.security.PatientAccess;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@Service
@Validated
@Transactional(readOnly = true)
public class PatientService {

	private final PatientRepository patientRepository;
	private final PatientAccess access;
	private final PatientAuditRepository auditRepository;

	public PatientService(PatientRepository patientRepository, PatientAccess access,
			PatientAuditRepository auditRepository) {
		this.patientRepository = patientRepository;
		this.access = access;
		this.auditRepository = auditRepository;
	}

	@Transactional
	public Patient create(@NotNull UUID clinicId, @NotNull @Valid PatientRequest request) {
		String actor = access.requireOperation(clinicId, "patients:create", true);
		Patient patient = patientRepository.save(new Patient(clinicId, request, actor));
		auditRepository.save(new PatientAuditEvent(patient, actor, "CREATE"));
		return patient;
	}

	@Transactional
	public Patient update(@NotNull UUID clinicId, @NotNull UUID id,
			@NotNull @Valid PatientRequest request, @NotNull @PositiveOrZero Long expectedVersion) {
		String actor = access.requireOperation(clinicId, "patients:write", true);
		Patient patient = findPatient(clinicId, id);
		access.requireResource(patient);
		if (!Objects.equals(patient.getVersion(), expectedVersion)) {
			throw new ObjectOptimisticLockingFailureException(Patient.class, id);
		}
		patient.update(request, actor);
		patientRepository.save(patient);
		auditRepository.save(new PatientAuditEvent(patient, actor, "UPDATE"));
		return patient;
	}

	@Transactional
	public Patient getById(@NotNull UUID clinicId, @NotNull UUID id) {
		String actor = access.requireOperation(clinicId, "patients:read", false);
		Patient patient = findPatient(clinicId, id);
		access.requireResource(patient);
		auditRepository.save(new PatientAuditEvent(patient, actor, "READ"));
		return patient;
	}

	@Transactional
	public void delete(@NotNull UUID clinicId, @NotNull UUID id) {
		String actor = access.requireOperation(clinicId, "patients:write", true);
		Patient patient = patientRepository.findByIdAndClinicId(id, clinicId)
				.orElseThrow(PatientNotFoundException::new);
		access.requireResource(patient);
		if (patient.getDeletedAt() == null) {
			patient.deactivate(actor);
			patientRepository.save(patient);
			auditRepository.save(new PatientAuditEvent(patient, actor, "DEACTIVATE"));
		}
	}

	private Patient findPatient(UUID clinicId, UUID id) {
		return patientRepository.findByIdAndClinicIdAndDeletedAtIsNull(id, clinicId)
				.orElseThrow(PatientNotFoundException::new);
	}
}