CREATE TABLE patients (
    id UUID PRIMARY KEY,
    clinic_id UUID NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(1024) NOT NULL,
    updated_by VARCHAR(1024) NOT NULL,
    deleted_by VARCHAR(1024),
    first_name VARCHAR(1024) NOT NULL,
    last_name VARCHAR(1024) NOT NULL,
    email VARCHAR(1024) NOT NULL
);

CREATE TABLE patient_audit_events (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    clinic_id UUID NOT NULL,
    actor VARCHAR(1024) NOT NULL,
    action VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);