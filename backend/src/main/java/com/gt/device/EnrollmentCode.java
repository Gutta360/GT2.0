package com.gt.device;

import com.gt.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollment_codes")
public class EnrollmentCode {
    @Id
    @Column(name = "enrollment_code_id", nullable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @Column(name = "code_hash", nullable = false, length = 64, unique = true)
    private String codeHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EnrollmentCode() {}

    public EnrollmentCode(UUID id, Tenant tenant, String codeHash, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.tenant = tenant;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public boolean canUseAt(Instant now) { return usedAt == null && expiresAt.isAfter(now) && tenant.isActive(); }
    public void consume(Instant now) { this.usedAt = now; }
}
