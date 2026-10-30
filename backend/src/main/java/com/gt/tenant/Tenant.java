package com.gt.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {
    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID id;
    @Column(name = "tenant_code", nullable = false, length = 50, unique = true)
    private String code;
    @Column(name = "tenant_name", nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tenant() {}

    public Tenant(UUID id, String code, String name, Instant now) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.status = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isActive() { return "ACTIVE".equals(status); }
}
