package com.gt.sync;

import com.gt.tenant.Tenant;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_event_results")
public class SyncEventResult {
    @Id @Column(name = "event_result_id") private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tenant_id") private Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "batch_id") private SyncBatch batch;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "event_index", nullable = false) private int eventIndex;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "result_code", length = 80) private String code;
    @Column(name = "result_message", length = 500) private String message;
    @Column(name = "acknowledged_at") private Instant acknowledgedAt;

    protected SyncEventResult() {}

    public SyncEventResult(Tenant tenant, SyncBatch batch, int index, SyncDtos.EventResponse response) {
        id = UUID.randomUUID(); this.tenant = tenant; this.batch = batch; eventId = response.eventId();
        eventIndex = index; status = response.status(); code = response.code(); message = response.message();
        acknowledgedAt = response.acknowledgedAt();
    }

    public SyncDtos.EventResponse toResponse() {
        return new SyncDtos.EventResponse(eventId, status, code, message, acknowledgedAt);
    }
}
