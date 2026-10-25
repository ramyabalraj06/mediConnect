package com.demo.mediconnect.patient;

public final class PatientMapper {

	private PatientMapper() {
	}

	public static PatientRequest toCommand(PatientCreateRequest request) {
		return new PatientRequest(request.firstName(), request.lastName(), request.email());
	}

	public static PatientRequest toCommand(PatientUpdateRequest request) {
		return new PatientRequest(request.firstName(), request.lastName(), request.email());
	}

	public static PatientResponse toResponse(Patient patient) {
		return new PatientResponse(patient.getId(), patient.getFirstName(), patient.getLastName(),
				patient.getEmail(), patient.getVersion());
	}
}