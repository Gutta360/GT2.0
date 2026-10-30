package com.gt.sync;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.gt.device.Device;
import com.gt.device.DeviceRepository;
import com.gt.patient.Patient;
import com.gt.patient.PatientRepository;
import com.gt.security.SecretHashing;
import com.gt.sync.SyncDtos.EventRequest;
import com.gt.sync.SyncDtos.EventResponse;
import com.gt.sync.SyncDtos.PatientEventPayload;
import com.gt.sync.dto.PatientPayload;
import com.gt.tenant.Tenant;
import com.gt.tenant.TenantRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class SyncEventProcessor {
    private final ProcessedSyncEventRepository processedEvents;
    private final SyncBatchRepository batches;
    private final TenantRepository tenants;
    private final DeviceRepository devices;
    private final PatientRepository patients;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final SecretHashing hashing;

    public SyncEventProcessor(ProcessedSyncEventRepository processedEvents, SyncBatchRepository batches,
                              TenantRepository tenants, DeviceRepository devices, PatientRepository patients,
                              ObjectMapper mapper, Validator validator, SecretHashing hashing) {
        this.processedEvents = processedEvents; this.batches = batches; this.tenants = tenants;
        this.devices = devices; this.patients = patients; this.mapper = mapper; this.validator = validator;
        this.hashing = hashing;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventResponse process(UUID tenantId, UUID deviceId, UUID batchId, EventRequest event) {
        Instant now = Instant.now();
        String requestHash = hash(event);
        ProcessedSyncEvent prior = processedEvents.findByTenant_IdAndEventId(tenantId, event.eventId()).orElse(null);
        if (prior != null) {
            if (prior.getRequestHash().equals(requestHash)) {
                return new EventResponse(event.eventId(), "ALREADY_PROCESSED", "IDEMPOTENT_REPLAY",
                        "The event was already applied.", now);
            }
            return rejected(event, "EVENT_ID_REUSED", "The event ID was already used for different content.", now);
        }

        EventResponse envelopeError = validateEnvelope(event, now);
        if (envelopeError != null) return envelopeError;

        PatientEventPayload wrapper;
        try {
            wrapper = mapper.treeToValue(event.payload(), PatientEventPayload.class);
        } catch (JacksonException error) {
            return rejected(event, "INVALID_PAYLOAD", "The patient payload cannot be parsed.", now);
        }
        PatientPayload payload = wrapper.patient();
        Set<ConstraintViolation<PatientEventPayload>> violations = validator.validate(wrapper);
        if (!violations.isEmpty()) return rejected(event, "INVALID_PATIENT", "Patient validation failed.", now);
        if (wrapper.contractVersion() != 1 || !"PATIENT".equals(wrapper.entityType()) ||
                !event.operation().equals(wrapper.operation()) || !event.entityId().equals(payload.id()) ||
                event.entityVersion() != payload.recordVersion()) {
            return rejected(event, "PAYLOAD_MISMATCH", "Payload metadata does not match the event envelope.", now);
        }
        if ("DOCTOR".equals(payload.referredBy()) &&
                (payload.doctorName() == null || payload.doctorName().trim().length() < 2)) {
            return rejected(event, "INVALID_DOCTOR", "Doctor name is required for doctor referrals.", now);
        }
        if (payload.updatedAt().isBefore(payload.createdAt())) {
            return rejected(event, "INVALID_TIMESTAMPS", "Patient updatedAt cannot precede createdAt.", now);
        }
        if (!event.createdAt().equals(payload.updatedAt())) {
            return rejected(event, "PAYLOAD_MISMATCH", "Event createdAt must match patient updatedAt.", now);
        }
        if ("DELETE".equals(event.operation())) {
            if (payload.active() || payload.deletedAt() == null) {
                return rejected(event, "INVALID_DELETE_STATE", "Delete requires an inactive patient with deletedAt.", now);
            }
        } else if (!payload.active() || payload.deletedAt() != null) {
            return rejected(event, "INVALID_ACTIVE_STATE", "Create and update require an active, non-deleted patient.", now);
        }

        Patient patient = patients.findByIdAndTenant_Id(event.entityId(), tenantId).orElse(null);
        if ("CREATE".equals(event.operation())) {
            if (patient != null || event.entityVersion() != 1) {
                return rejected(event, "CREATE_CONFLICT", "Create requires a new patient at version 1.", now);
            }
            Tenant tenant = tenants.findById(tenantId).orElseThrow();
            patient = new Patient(event.entityId(), tenant);
        } else {
            if (patient == null) return rejected(event, "PATIENT_NOT_FOUND", "Patient does not exist.", now);
            if (event.entityVersion() != patient.getRecordVersion() + 1) {
                return rejected(event, "STALE_OR_GAPPED_VERSION", "Patient versions must be uploaded in order.", now);
            }
            if (!patient.getPatientNumber().equals(payload.patientNumber()) ||
                    !patient.getCreatedAt().equals(payload.createdAt())) {
                return rejected(event, "IMMUTABLE_FIELD_CHANGED", "Patient number and createdAt are immutable.", now);
            }
        }

        patient.apply(payload);
        patients.saveAndFlush(patient);
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        Device device = devices.findByIdAndTenant_Id(deviceId, tenantId).orElseThrow();
        SyncBatch batch = batches.findByIdAndTenant_Id(batchId, tenantId).orElseThrow();
        processedEvents.saveAndFlush(new ProcessedSyncEvent(tenant, device, batch, event, requestHash, now));
        return new EventResponse(event.eventId(), "ACCEPTED", "APPLIED", "Event applied.", now);
    }

    private EventResponse validateEnvelope(EventRequest event, Instant now) {
        if (event.contractVersion() != 1) return rejected(event, "UNSUPPORTED_CONTRACT", "Only contract version 1 is supported.", now);
        if (!"PATIENT".equals(event.entityType())) return rejected(event, "UNSUPPORTED_ENTITY", "Only PATIENT events are supported.", now);
        if (!Set.of("CREATE", "UPDATE", "DELETE").contains(event.operation()))
            return rejected(event, "UNSUPPORTED_OPERATION", "Operation is unsupported.", now);
        return null;
    }

    private String hash(EventRequest event) {
        try { return hashing.sha256(mapper.writeValueAsString(event)); }
        catch (JacksonException error) { throw new IllegalArgumentException("Event cannot be serialized", error); }
    }

    private EventResponse rejected(EventRequest event, String code, String message, Instant now) {
        return new EventResponse(event.eventId(), "REJECTED", code, message, now);
    }
}
