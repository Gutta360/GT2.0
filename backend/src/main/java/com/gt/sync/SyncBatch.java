package com.gt.sync;

import com.gt.device.Device;
import com.gt.tenant.Tenant;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_batches")
public class SyncBatch {
    @Id @Column(name = "batch_id", nullable = false) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tenant_id") private Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "device_id") private Device device;
    @Column(name = "contract_version", nullable = false) private int contractVersion;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "accepted_count", nullable = false) private int acceptedCount;
    @Column(name = "duplicate_count", nullable = false) private int duplicateCount;
    @Column(name = "rejected_count", nullable = false) private int rejectedCount;
    @Column(name = "retryable_count", nullable = false) private int retryableCount;

    protected SyncBatch() {}

    public SyncBatch(UUID id, Tenant tenant, Device device, int contractVersion, String requestHash, Instant now) {
        this.id = id; this.tenant = tenant; this.device = device; this.contractVersion = contractVersion;
        this.requestHash = requestHash; this.receivedAt = now;
    }

    public void complete(int accepted, int duplicate, int rejected, int retryable, Instant now) {
        acceptedCount = accepted; duplicateCount = duplicate; rejectedCount = rejected;
        retryableCount = retryable; completedAt = now;
    }

    public UUID getId() { return id; }
    public String getRequestHash() { return requestHash; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
