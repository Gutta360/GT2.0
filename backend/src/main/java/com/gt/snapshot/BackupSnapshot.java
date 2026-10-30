package com.gt.snapshot;

import com.gt.device.Device;
import com.gt.tenant.Tenant;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backup_snapshots")
public class BackupSnapshot {
    @Id @Column(name = "snapshot_id") private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tenant_id") private Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "device_id") private Device device;
    @Column(name = "snapshot_contract_version", nullable = false) private int contractVersion;
    @Column(name = "local_schema_version", nullable = false) private int localSchemaVersion;
    @Column(name = "snapshot_created_at", nullable = false) private Instant snapshotCreatedAt;
    @Column(name = "uploaded_at", nullable = false) private Instant uploadedAt;
    @Column(name = "patient_count", nullable = false) private int patientCount;
    @Column(name = "last_acknowledged_event_id") private UUID lastAcknowledgedEventId;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(name = "content_length", nullable = false) private long contentLength;
    @Column(name = "storage_key", nullable = false, length = 255) private String storageKey;
    @Column(nullable = false, length = 20) private String status;

    protected BackupSnapshot() {}

    public BackupSnapshot(UUID id, Tenant tenant, Device device, SnapshotDtos.SnapshotDocument document,
                          String sha256, long contentLength, String storageKey, Instant uploadedAt) {
        this.id = id; this.tenant = tenant; this.device = device; contractVersion = document.contractVersion();
        localSchemaVersion = document.localSchemaVersion(); snapshotCreatedAt = document.createdAt();
        patientCount = document.patients().size(); lastAcknowledgedEventId = document.lastAcknowledgedEventId();
        this.sha256 = sha256; this.contentLength = contentLength; this.storageKey = storageKey;
        this.uploadedAt = uploadedAt; status = "AVAILABLE";
    }

    public void markDeleted() { status = "DELETED"; }
    public UUID getId() { return id; }
    public int getContractVersion() { return contractVersion; }
    public int getLocalSchemaVersion() { return localSchemaVersion; }
    public Instant getSnapshotCreatedAt() { return snapshotCreatedAt; }
    public Instant getUploadedAt() { return uploadedAt; }
    public int getPatientCount() { return patientCount; }
    public UUID getLastAcknowledgedEventId() { return lastAcknowledgedEventId; }
    public String getSha256() { return sha256; }
    public long getContentLength() { return contentLength; }
    public String getStorageKey() { return storageKey; }
    public String getStatus() { return status; }
}
