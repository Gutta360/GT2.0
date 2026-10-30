package com.gt.sync;

import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SyncDtos {
    private SyncDtos() {}

    public record BatchRequest(
            @Min(1) int contractVersion,
            @NotNull UUID deviceId,
            @NotNull UUID batchId,
            @NotEmpty List<@Valid EventRequest> events) {}

    public record EventRequest(
            @NotNull UUID eventId,
            @Min(1) int contractVersion,
            @NotBlank String entityType,
            @NotNull UUID entityId,
            @NotBlank String operation,
            @NotNull JsonNode payload,
            @Min(1) long entityVersion,
            @NotNull Instant createdAt) {}

    public record EventResponse(UUID eventId, String status, String code, String message, Instant acknowledgedAt) {}

    public record BatchResponse(UUID batchId, Instant receivedAt, Instant completedAt,
                                int acceptedCount, int duplicateCount, int rejectedCount, int retryableCount,
                                List<EventResponse> results) {}

    public record PatientEventPayload(int contractVersion, String entityType, String operation,
                                      @Valid @NotNull com.gt.sync.dto.PatientPayload patient) {}
}
