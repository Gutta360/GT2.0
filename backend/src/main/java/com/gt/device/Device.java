package com.gt.device;

import com.gt.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {
    @Id
    @Column(name = "device_id", nullable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @Column(name = "device_name", nullable = false, length = 120)
    private String name;
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;
    @Column(name = "registration_status", nullable = false, length = 20)
    private String status;
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Device() {}

    public Device(UUID id, Tenant tenant, String name, String tokenHash, Instant now) {
        this.id = id;
        this.tenant = tenant;
        this.name = name;
        this.tokenHash = tokenHash;
        this.status = "ACTIVE";
        this.registeredAt = now;
        this.lastSeenAt = now;
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public boolean isActive() { return "ACTIVE".equals(status) && tenant.isActive(); }
    public void revoke(Instant now) { status = "REVOKED"; revokedAt = now; }
    public void touch(Instant now) {
        if (lastSeenAt == null || Duration.between(lastSeenAt, now).toMinutes() >= 5) lastSeenAt = now;
    }
}
