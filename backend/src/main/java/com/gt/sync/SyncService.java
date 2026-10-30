package com.gt.sync;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.gt.config.ApiException;
import com.gt.config.Gt2Properties;
import com.gt.security.DevicePrincipal;
import com.gt.security.SecretHashing;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class SyncService {
    private final SyncBatchStore store;
    private final SyncEventProcessor processor;
    private final Gt2Properties properties;
    private final ObjectMapper mapper;
    private final SecretHashing hashing;

    public SyncService(SyncBatchStore store, SyncEventProcessor processor, Gt2Properties properties,
                       ObjectMapper mapper, SecretHashing hashing) {
        this.store = store; this.processor = processor; this.properties = properties;
        this.mapper = mapper; this.hashing = hashing;
    }

    public SyncDtos.BatchResponse synchronize(DevicePrincipal principal, SyncDtos.BatchRequest request) {
        if (!principal.deviceId().equals(request.deviceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEVICE_MISMATCH", "Authenticated and requested devices differ.");
        }
        if (request.contractVersion() != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CONTRACT", "Only contract version 1 is supported.");
        }
        if (request.events().size() > properties.maxBatchSize()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "BATCH_TOO_LARGE", "The batch exceeds the configured event limit.");
        }
        SyncBatchStore.OpenedBatch opened = store.open(principal.tenantId(), principal.deviceId(), request, hash(request));
        if (opened.existing() && opened.batch().getCompletedAt() != null) {
            return response(opened.batch(), opened.results());
        }

        List<SyncDtos.EventResponse> results = new ArrayList<>(opened.results());
        int start = results.size();
        for (int index = start; index < request.events().size(); index++) {
            SyncDtos.EventRequest event = request.events().get(index);
            SyncDtos.EventResponse result;
            try {
                result = processor.process(principal.tenantId(), principal.deviceId(), request.batchId(), event);
            } catch (DataIntegrityViolationException error) {
                result = new SyncDtos.EventResponse(event.eventId(), "REJECTED", "DATABASE_CONSTRAINT",
                        "Event conflicts with existing tenant data.", Instant.now());
            } catch (RuntimeException error) {
                result = new SyncDtos.EventResponse(event.eventId(), "RETRYABLE_FAILURE", "SERVER_ERROR",
                        "Event was not applied; retry is safe.", Instant.now());
            }
            store.append(principal.tenantId(), request.batchId(), index, result);
            results.add(result);
        }
        int accepted = count(results, "ACCEPTED");
        int duplicates = count(results, "ALREADY_PROCESSED");
        int rejected = count(results, "REJECTED");
        int retryable = count(results, "RETRYABLE_FAILURE");
        Instant completed = Instant.now();
        store.complete(principal.tenantId(), request.batchId(), accepted, duplicates, rejected, retryable, completed);
        return new SyncDtos.BatchResponse(request.batchId(), opened.batch().getReceivedAt(), completed,
                accepted, duplicates, rejected, retryable, List.copyOf(results));
    }

    private int count(List<SyncDtos.EventResponse> results, String status) {
        return (int) results.stream().filter(value -> status.equals(value.status())).count();
    }

    private String hash(SyncDtos.BatchRequest request) {
        try { return hashing.sha256(mapper.writeValueAsString(request)); }
        catch (JacksonException error) { throw new IllegalArgumentException("Batch cannot be serialized", error); }
    }

    private SyncDtos.BatchResponse response(SyncBatch batch, List<SyncDtos.EventResponse> results) {
        return new SyncDtos.BatchResponse(batch.getId(), batch.getReceivedAt(), batch.getCompletedAt(),
                count(results, "ACCEPTED"), count(results, "ALREADY_PROCESSED"),
                count(results, "REJECTED"), count(results, "RETRYABLE_FAILURE"), results);
    }
}
