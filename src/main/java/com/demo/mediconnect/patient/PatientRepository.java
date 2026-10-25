package com.demo.mediconnect.patient;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface PatientRepository extends Repository<Patient, UUID> {

	Patient save(Patient patient);

	Optional<Patient> findByIdAndClinicId(UUID id, UUID clinicId);

	Optional<Patient> findByIdAndClinicIdAndDeletedAtIsNull(UUID id, UUID clinicId);
}