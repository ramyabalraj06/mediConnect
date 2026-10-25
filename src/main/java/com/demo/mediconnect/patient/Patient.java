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
import jakarta.persistence.Version;

@Entity
@Table(name = "patients")
public class Patient {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID clinicId;

	@Version
	private Long version;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	private Instant deletedAt;

	@Convert(converter = PatientFieldEncryptionConverter.class)
	@Column(nullable = false, updatable = false, length = 1024)
	private String createdBy;

	@Convert(converter = PatientFieldEncryptionConverter.class)
	@Column(nullable = false, length = 1024)
	private String updatedBy;

	@Convert(converter = PatientFieldEncryptionConverter.class)
	@Column(length = 1024)
	private String deletedBy;

	@Convert(converter = PatientFieldEncryptionConverter.class)
	@Column(nullable = false, length = 1024)
	private String firstName;

	@Convert(converter = PatientFieldEncryptionConverter.class)
	@Column(nullable = false, length = 1024)
	private String lastName;

	@Convert(converter = PatientFieldEncryptionConverter.class)
	@Column(nullable = false, length = 1024)
	private String email;

	protected Patient() {
	}

	Patient(UUID clinicId, PatientRequest request, String actor) {
		this.clinicId = clinicId;
		this.createdAt = Instant.now();
		this.createdBy = actor;
		update(request, actor);
	}

	void update(PatientRequest request, String actor) {
		this.firstName = request.firstName();
		this.lastName = request.lastName();
		this.email = request.email();
		this.updatedAt = Instant.now();
		this.updatedBy = actor;
	}

	void deactivate(String actor) {
		if (deletedAt == null) {
			deletedAt = Instant.now();
			updatedAt = deletedAt;
			deletedBy = actor;
			updatedBy = actor;
		}
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Long getVersion() {
		return version;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getClinicId() {
		return clinicId;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public String getEmail() {
		return email;
	}
}