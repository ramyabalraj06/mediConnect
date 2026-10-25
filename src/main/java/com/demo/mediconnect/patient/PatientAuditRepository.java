package com.demo.mediconnect.patient;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface PatientAuditRepository extends Repository<PatientAuditEvent, UUID> {

	PatientAuditEvent save(PatientAuditEvent event);

	List<PatientAuditEvent> findByPatientIdAndClinicIdOrderByOccurredAtAsc(UUID patientId, UUID clinicId);
}