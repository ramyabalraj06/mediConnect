package com.demo.mediconnect.patient;

public class PatientNotFoundException extends RuntimeException {

	public PatientNotFoundException() {
		super("Patient not found");
	}
}