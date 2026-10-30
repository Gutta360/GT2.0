package com.gt.sync;

import com.gt.config.ApiException;
import com.gt.device.Device;
import com.gt.device.DeviceRepository;
import com.gt.tenant.Tenant;
import com.gt.tenant.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SyncBatchStore {
    private final SyncBatchRepository batches;
    private final SyncEventResultRepository results;
    private final TenantRepository tenants;
    private final DeviceRepository devices;

    public SyncBatchStore(SyncBatchRepository batches, SyncEventResultRepository results,
                          TenantRepository tenants, DeviceRepository devices) {
        this.batches = batches; this.results = results; this.tenants = tenants; this.devices = devices;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OpenedBatch open(UUID tenantId, UUID deviceId, SyncDtos.BatchRequest request, String requestHash) {
        SyncBatch existing = batches.findByIdAndTenant_Id(request.batchId(), tenantId).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "BATCH_ID_REUSED",
                        "The batch ID was already used for different content.");
            }
            List<SyncDtos.EventResponse> saved = results.findAllByBatch_IdOrderByEventIndex(existing.getId())
                    .stream().map(SyncEventResult::toResponse).toList();
            return new OpenedBatch(existing, true, saved);
        }
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        Device device = devices.findByIdAndTenant_Id(deviceId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "DEVICE_MISMATCH", "Device is not registered to this tenant."));
        SyncBatch created = batches.saveAndFlush(new SyncBatch(request.batchId(), tenant, device,
                request.contractVersion(), requestHash, Instant.now()));
        return new OpenedBatch(created, false, List.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(UUID tenantId, UUID batchId, int index, SyncDtos.EventResponse response) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        SyncBatch batch = batches.findByIdAndTenant_Id(batchId, tenantId).orElseThrow();
        results.save(new SyncEventResult(tenant, batch, index, response));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID tenantId, UUID batchId, int accepted, int duplicate, int rejected, int retryable, Instant now) {
        SyncBatch batch = batches.findByIdAndTenant_Id(batchId, tenantId).orElseThrow();
        batch.complete(accepted, duplicate, rejected, retryable, now);
    }

    public record OpenedBatch(SyncBatch batch, boolean existing, List<SyncDtos.EventResponse> results) {}
}
