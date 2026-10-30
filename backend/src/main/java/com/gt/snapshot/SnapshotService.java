package com.gt.snapshot;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.gt.config.ApiException;
import com.gt.config.Gt2Properties;
import com.gt.device.Device;
import com.gt.device.DeviceRepository;
import com.gt.security.DevicePrincipal;
import com.gt.security.SecretHashing;
import com.gt.patient.PatientRepository;
import com.gt.sync.ProcessedSyncEventRepository;
import com.gt.sync.dto.PatientPayload;
import com.gt.tenant.Tenant;
import com.gt.tenant.TenantRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SnapshotService {
    private final BackupSnapshotRepository snapshots;
    private final RecoveryAuditRepository audits;
    private final TenantRepository tenants;
    private final DeviceRepository devices;
    private final SnapshotStorage storage;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final SecretHashing hashing;
    private final Gt2Properties properties;
    private final PatientRepository patients;
    private final ProcessedSyncEventRepository processedEvents;

    public SnapshotService(BackupSnapshotRepository snapshots, RecoveryAuditRepository audits,
                           TenantRepository tenants, DeviceRepository devices, SnapshotStorage storage,
                           ObjectMapper mapper, Validator validator, SecretHashing hashing, Gt2Properties properties,
                           PatientRepository patients, ProcessedSyncEventRepository processedEvents) {
        this.snapshots = snapshots; this.audits = audits; this.tenants = tenants; this.devices = devices;
        this.storage = storage; this.mapper = mapper; this.validator = validator; this.hashing = hashing;
        this.properties = properties;
        this.patients = patients; this.processedEvents = processedEvents;
    }

    @Transactional
    public SnapshotDtos.SnapshotResponse upload(DevicePrincipal principal, SnapshotDtos.UploadRequest request) {
        byte[] content = request.snapshot().getBytes(StandardCharsets.UTF_8);
        if (content.length > properties.maxSnapshotBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "SNAPSHOT_TOO_LARGE", "Snapshot exceeds the configured size limit.");
        }
        if (!hashing.constantTimeEquals(hashing.sha256(request.snapshot()), request.sha256())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHECKSUM_MISMATCH", "Snapshot checksum does not match its content.");
        }
        SnapshotDtos.SnapshotDocument document = parse(request.snapshot());
        validate(document);
        Tenant tenant = tenants.findById(principal.tenantId()).orElseThrow();
        Device device = devices.findByIdAndTenant_Id(principal.deviceId(), principal.tenantId()).orElseThrow();
        UUID id = UUID.randomUUID();
        String storageKey = storage.store(principal.tenantId(), id, content);
        BackupSnapshot saved;
        try {
            saved = snapshots.saveAndFlush(new BackupSnapshot(id, tenant, device, document, request.sha256(),
                    content.length, storageKey, Instant.now()));
        } catch (RuntimeException error) {
            storage.delete(storageKey);
            throw error;
        }
        enforceRetention(principal.tenantId());
        return SnapshotDtos.SnapshotResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SnapshotDtos.SnapshotResponse> list(DevicePrincipal principal) {
        return snapshots.findAllByTenant_IdAndStatusOrderByUploadedAtDesc(principal.tenantId(), "AVAILABLE")
                .stream().map(SnapshotDtos.SnapshotResponse::from).toList();
    }

    @Transactional
    public Download download(DevicePrincipal principal, UUID snapshotId) {
        BackupSnapshot snapshot = available(principal, snapshotId);
        byte[] content = storage.load(snapshot.getStorageKey(), principal.tenantId(), snapshotId);
        String actual = hashing.sha256(new String(content, StandardCharsets.UTF_8));
        if (!hashing.constantTimeEquals(actual, snapshot.getSha256())) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORED_CHECKSUM_MISMATCH",
                    "Stored snapshot integrity verification failed.");
        }
        audit(principal, snapshot, "DOWNLOAD", "SUCCESS", "Snapshot downloaded for clean-device recovery.");
        return new Download(content, snapshot.getSha256());
    }

    @Transactional
    public Download recoveryPackage(DevicePrincipal principal, UUID snapshotId) {
        BackupSnapshot snapshot = available(principal, snapshotId);
        byte[] baselineBytes = storage.load(snapshot.getStorageKey(), principal.tenantId(), snapshotId);
        String baseline = new String(baselineBytes, StandardCharsets.UTF_8);
        if (!hashing.constantTimeEquals(hashing.sha256(baseline), snapshot.getSha256())) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORED_CHECKSUM_MISMATCH",
                    "Stored snapshot integrity verification failed.");
        }
        SnapshotDtos.SnapshotDocument document = parse(baseline);
        Map<UUID, PatientPayload> merged = new HashMap<>();
        document.patients().forEach(patient -> merged.put(patient.id(), patient));
        patients.findAllByTenant_IdOrderByPatientNumber(principal.tenantId()).stream()
                .map(com.gt.patient.Patient::toPayload)
                .forEach(server -> merged.compute(server.id(), (id, local) ->
                        local == null || server.recordVersion() >= local.recordVersion() ? server : local));
        UUID latestEventId = processedEvents.findFirstByTenant_IdOrderByProcessedAtDesc(principal.tenantId())
                .map(com.gt.sync.ProcessedSyncEvent::getEventId).orElse(document.lastAcknowledgedEventId());
        SnapshotDtos.SnapshotDocument recovery = new SnapshotDtos.SnapshotDocument(1, document.localSchemaVersion(),
                Instant.now(), latestEventId,
                merged.values().stream().sorted(Comparator.comparing(PatientPayload::patientNumber)).toList());
        try {
            String content = mapper.writeValueAsString(recovery);
            audit(principal, snapshot, "RECOVERY_PACKAGE", "SUCCESS",
                    "Combined snapshot with latest accepted server patient versions.");
            return new Download(content.getBytes(StandardCharsets.UTF_8), hashing.sha256(content));
        } catch (JacksonException error) {
            throw new IllegalStateException("Recovery package serialization failed", error);
        }
    }

    @Transactional
    public void recordRecovery(DevicePrincipal principal, UUID snapshotId, SnapshotDtos.RecoveryRequest request) {
        BackupSnapshot snapshot = available(principal, snapshotId);
        audit(principal, snapshot, "RESTORE", request.outcome(), request.details());
    }

    private BackupSnapshot available(DevicePrincipal principal, UUID snapshotId) {
        return snapshots.findByIdAndTenant_IdAndStatus(snapshotId, principal.tenantId(), "AVAILABLE")
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SNAPSHOT_NOT_FOUND", "Snapshot was not found."));
    }

    private void audit(DevicePrincipal principal, BackupSnapshot snapshot, String action, String outcome, String details) {
        Tenant tenant = tenants.findById(principal.tenantId()).orElseThrow();
        Device device = devices.findByIdAndTenant_Id(principal.deviceId(), principal.tenantId()).orElseThrow();
        audits.save(new RecoveryAudit(tenant, device, snapshot, action, outcome, details));
    }

    private SnapshotDtos.SnapshotDocument parse(String content) {
        try { return mapper.readValue(content, SnapshotDtos.SnapshotDocument.class); }
        catch (JacksonException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SNAPSHOT", "Snapshot document cannot be parsed.");
        }
    }

    private void validate(SnapshotDtos.SnapshotDocument document) {
        Set<ConstraintViolation<SnapshotDtos.SnapshotDocument>> violations = validator.validate(document);
        if (!violations.isEmpty() || document.contractVersion() != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SNAPSHOT", "Snapshot document validation failed.");
        }
        Set<UUID> ids = new HashSet<>();
        for (PatientPayload patient : document.patients()) {
            if (!ids.add(patient.id())) throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_PATIENT", "Snapshot contains duplicate patients.");
            if ("DOCTOR".equals(patient.referredBy()) &&
                    (patient.doctorName() == null || patient.doctorName().trim().length() < 2)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SNAPSHOT", "Snapshot contains an invalid doctor referral.");
            }
        }
    }

    private void enforceRetention(UUID tenantId) {
        List<BackupSnapshot> all = snapshots.findAllByTenant_IdAndStatusOrderByUploadedAtDesc(tenantId, "AVAILABLE");
        all.stream().skip(properties.snapshotRetentionCount()).forEach(snapshot -> {
            storage.delete(snapshot.getStorageKey());
            snapshot.markDeleted();
        });
    }

    public record Download(byte[] content, String sha256) {}
}
