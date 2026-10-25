package com.demo.mediconnect.patient;

import java.util.UUID;

public record PatientResponse(UUID id, String firstName, String lastName, String email, Long version) {
}