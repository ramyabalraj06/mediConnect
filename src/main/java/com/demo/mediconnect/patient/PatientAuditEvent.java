package com.demo.mediconnect.patient;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient_audit_events")
public class PatientAuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID patientId;

	@Column(nullable = false, updatable = false)
	private UUID clinicId;

	@Convert(converter = PatientFieldEncryptionConverter.class)
	@Column(nullable = false, updatable = false, length = 1024)
	private String actor;

	@Column(nullable = false, updatable = false)
	private String action;

	@Column(nullable = false, updatable = false)
	private Instant occurredAt;

	protected PatientAuditEvent() {
	}

	PatientAuditEvent(Patient patient, String actor, String action) {
		this.patientId = patient.getId();
		this.clinicId = patient.getClinicId();
		this.actor = actor;
		this.action = action;
		this.occurredAt = Instant.now();
	}

	public String getAction() {
		return action;
	}
}