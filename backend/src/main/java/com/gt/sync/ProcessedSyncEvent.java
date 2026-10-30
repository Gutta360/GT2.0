package com.gt.sync;

import tools.jackson.databind.JsonNode;
import com.gt.device.Device;
import com.gt.tenant.Tenant;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_sync_events")
public class ProcessedSyncEvent {
    @Id @Column(name = "processed_event_id") private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tenant_id") private Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "device_id") private Device device;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "batch_id") private SyncBatch batch;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "entity_type", nullable = false, length = 40) private String entityType;
    @Column(name = "entity_id", nullable = false) private UUID entityId;
    @Column(nullable = false, length = 20) private String operation;
    @Column(name = "entity_version", nullable = false) private long entityVersion;
    @Column(name = "event_created_at", nullable = false) private Instant eventCreatedAt;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private JsonNode payload;
    @Column(name = "processed_at", nullable = false) private Instant processedAt;

    protected ProcessedSyncEvent() {}

    public ProcessedSyncEvent(Tenant tenant, Device device, SyncBatch batch, SyncDtos.EventRequest event,
                              String requestHash, Instant now) {
        id = UUID.randomUUID(); this.tenant = tenant; this.device = device; this.batch = batch;
        eventId = event.eventId(); entityType = event.entityType(); entityId = event.entityId();
        operation = event.operation(); entityVersion = event.entityVersion(); eventCreatedAt = event.createdAt();
        this.requestHash = requestHash; payload = event.payload(); processedAt = now;
    }

    public String getRequestHash() { return requestHash; }
    public UUID getEventId() { return eventId; }
    public Instant getProcessedAt() { return processedAt; }
}
