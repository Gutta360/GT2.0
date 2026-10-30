package com.gt.snapshot;

import com.gt.device.Device;
import com.gt.tenant.Tenant;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_audit")
public class RecoveryAudit {
    @Id @Column(name = "recovery_audit_id") private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tenant_id") private Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "device_id") private Device device;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "snapshot_id") private BackupSnapshot snapshot;
    @Column(nullable = false, length = 40) private String action;
    @Column(nullable = false, length = 20) private String outcome;
    @Column(length = 500) private String details;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected RecoveryAudit() {}
    public RecoveryAudit(Tenant tenant, Device device, BackupSnapshot snapshot, String action, String outcome, String details) {
        id = UUID.randomUUID(); this.tenant = tenant; this.device = device; this.snapshot = snapshot;
        this.action = action; this.outcome = outcome; this.details = details; occurredAt = Instant.now();
    }
}
