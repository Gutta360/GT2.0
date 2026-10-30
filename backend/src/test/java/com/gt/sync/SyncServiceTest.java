package com.gt.sync;

import com.gt.config.Gt2Properties;
import com.gt.security.DevicePrincipal;
import com.gt.security.SecretHashing;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SyncServiceTest {
    @Test
    void preservesPartialBatchResultsAndCountsRetryableFailures() throws Exception {
        SyncBatchStore store = mock(SyncBatchStore.class);
        SyncEventProcessor processor = mock(SyncEventProcessor.class);
        ObjectMapper mapper = new ObjectMapper();
        Gt2Properties properties = new Gt2Properties("a".repeat(32), "key", Path.of("snapshots"),
                List.of("https://localhost"), Duration.ofMinutes(30), 100, 1_000_000, 5);
        SyncService service = new SyncService(store, processor, properties, mapper, new SecretHashing());
        UUID tenantId = UUID.randomUUID(), deviceId = UUID.randomUUID(), batchId = UUID.randomUUID();
        SyncDtos.EventRequest first = event(mapper, 1), second = event(mapper, 2);
        SyncDtos.BatchRequest request = new SyncDtos.BatchRequest(1, deviceId, batchId, List.of(first, second));
        SyncBatch batch = new SyncBatch(batchId, null, null, 1, "hash", Instant.now());
        when(store.open(eq(tenantId), eq(deviceId), eq(request), anyString()))
                .thenReturn(new SyncBatchStore.OpenedBatch(batch, false, List.of()));
        when(processor.process(tenantId, deviceId, batchId, first))
                .thenReturn(new SyncDtos.EventResponse(first.eventId(), "ACCEPTED", "APPLIED", "ok", Instant.now()));
        when(processor.process(tenantId, deviceId, batchId, second)).thenThrow(new IllegalStateException("temporary"));

        SyncDtos.BatchResponse response = service.synchronize(new DevicePrincipal(tenantId, deviceId, "T", "D"), request);

        assertThat(response.acceptedCount()).isEqualTo(1);
        assertThat(response.retryableCount()).isEqualTo(1);
        assertThat(response.results()).extracting(SyncDtos.EventResponse::status)
                .containsExactly("ACCEPTED", "RETRYABLE_FAILURE");
        verify(store, times(2)).append(eq(tenantId), eq(batchId), anyInt(), any());
        verify(store).complete(eq(tenantId), eq(batchId), eq(1), eq(0), eq(0), eq(1), any());
    }

    private SyncDtos.EventRequest event(ObjectMapper mapper, long version) throws Exception {
        return new SyncDtos.EventRequest(UUID.randomUUID(), 1, "PATIENT", UUID.randomUUID(), "CREATE",
                mapper.readTree("{}"), version, Instant.now());
    }
}
