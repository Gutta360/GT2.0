CREATE TABLE tenants (
    tenant_id UUID PRIMARY KEY,
    tenant_code VARCHAR(50) NOT NULL,
    tenant_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_tenants_code UNIQUE (tenant_code),
    CONSTRAINT chk_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE enrollment_codes (
    enrollment_code_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_enrollment_code_hash UNIQUE (code_hash)
);
CREATE INDEX ix_enrollment_codes_tenant ON enrollment_codes(tenant_id, expires_at);

CREATE TABLE devices (
    device_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    device_name VARCHAR(120) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    registration_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    registered_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uk_devices_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_devices_tenant_device UNIQUE (tenant_id, device_id),
    CONSTRAINT chk_devices_status CHECK (registration_status IN ('ACTIVE', 'REVOKED'))
);
CREATE INDEX ix_devices_tenant_status ON devices(tenant_id, registration_status);

CREATE TABLE patients (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    patient_number VARCHAR(20) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50),
    gender VARCHAR(10) NOT NULL,
    age SMALLINT NOT NULL,
    mobile VARCHAR(10) NOT NULL,
    address VARCHAR(255) NOT NULL,
    referred_by VARCHAR(20) NOT NULL,
    doctor_name VARCHAR(100),
    consultation_fee_cents BIGINT NOT NULL,
    registration_date TIMESTAMPTZ NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    record_version BIGINT NOT NULL,
    CONSTRAINT uk_patients_tenant_number UNIQUE (tenant_id, patient_number),
    CONSTRAINT uk_patients_tenant_name_mobile UNIQUE (tenant_id, first_name, mobile),
    CONSTRAINT chk_patients_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT chk_patients_age CHECK (age BETWEEN 1 AND 120),
    CONSTRAINT chk_patients_mobile CHECK (mobile ~ '^[0-9]{10}$'),
    CONSTRAINT chk_patients_referral CHECK (referred_by IN ('DOCTOR', 'PATIENT', 'ONLINE', 'SELF', 'OTHER')),
    CONSTRAINT chk_patients_doctor CHECK (referred_by <> 'DOCTOR' OR LENGTH(BTRIM(COALESCE(doctor_name, ''))) BETWEEN 2 AND 100),
    CONSTRAINT chk_patients_fee CHECK (consultation_fee_cents >= 0),
    CONSTRAINT chk_patients_version CHECK (record_version > 0)
);
CREATE UNIQUE INDEX uk_patients_tenant_normalized_name_mobile
    ON patients(tenant_id, LOWER(BTRIM(first_name)), mobile);
CREATE INDEX ix_patients_tenant_active_name
    ON patients(tenant_id, is_active, first_name, last_name, patient_number);

CREATE TABLE sync_batches (
    batch_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    device_id UUID NOT NULL REFERENCES devices(device_id),
    contract_version INTEGER NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    accepted_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    rejected_count INTEGER NOT NULL DEFAULT 0,
    retryable_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_sync_batches_tenant_batch UNIQUE (tenant_id, batch_id),
    CONSTRAINT chk_sync_batches_contract CHECK (contract_version > 0)
);
CREATE INDEX ix_sync_batches_tenant_received ON sync_batches(tenant_id, received_at DESC);

CREATE TABLE processed_sync_events (
    processed_event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    device_id UUID NOT NULL REFERENCES devices(device_id),
    batch_id UUID NOT NULL REFERENCES sync_batches(batch_id),
    event_id UUID NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id UUID NOT NULL,
    operation VARCHAR(20) NOT NULL,
    entity_version BIGINT NOT NULL,
    event_created_at TIMESTAMPTZ NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_processed_events_tenant_event UNIQUE (tenant_id, event_id),
    CONSTRAINT uk_processed_events_entity_version UNIQUE (tenant_id, entity_type, entity_id, entity_version),
    CONSTRAINT chk_processed_events_operation CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE'))
);
CREATE INDEX ix_processed_events_entity ON processed_sync_events(tenant_id, entity_type, entity_id, entity_version);

CREATE TABLE sync_event_results (
    event_result_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    batch_id UUID NOT NULL REFERENCES sync_batches(batch_id),
    event_id UUID NOT NULL,
    event_index INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    result_code VARCHAR(80),
    result_message VARCHAR(500),
    acknowledged_at TIMESTAMPTZ,
    CONSTRAINT uk_sync_event_results_batch_event UNIQUE (batch_id, event_id),
    CONSTRAINT chk_sync_event_results_status CHECK (status IN ('ACCEPTED', 'ALREADY_PROCESSED', 'REJECTED', 'RETRYABLE_FAILURE'))
);
CREATE INDEX ix_sync_event_results_batch_order ON sync_event_results(batch_id, event_index);

CREATE TABLE backup_snapshots (
    snapshot_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    device_id UUID NOT NULL REFERENCES devices(device_id),
    snapshot_contract_version INTEGER NOT NULL,
    local_schema_version INTEGER NOT NULL,
    snapshot_created_at TIMESTAMPTZ NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    patient_count INTEGER NOT NULL,
    last_acknowledged_event_id UUID,
    sha256 VARCHAR(64) NOT NULL,
    content_length BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uk_backup_snapshots_storage UNIQUE (storage_key),
    CONSTRAINT chk_backup_snapshots_status CHECK (status IN ('AVAILABLE', 'DELETED')),
    CONSTRAINT chk_backup_snapshots_counts CHECK (patient_count >= 0 AND content_length > 0)
);
CREATE INDEX ix_backup_snapshots_tenant_time ON backup_snapshots(tenant_id, uploaded_at DESC);

CREATE TABLE recovery_audit (
    recovery_audit_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    device_id UUID NOT NULL REFERENCES devices(device_id),
    snapshot_id UUID REFERENCES backup_snapshots(snapshot_id),
    action VARCHAR(40) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    details VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_recovery_outcome CHECK (outcome IN ('SUCCESS', 'FAILED'))
);
CREATE INDEX ix_recovery_audit_tenant_time ON recovery_audit(tenant_id, occurred_at DESC);
