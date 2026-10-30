package com.gt.snapshot;

import com.gt.sync.dto.PatientPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SnapshotDtos {
    private SnapshotDtos() {}

    public record UploadRequest(@NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String sha256,
                                @NotBlank String snapshot) {}

    public record SnapshotDocument(@Min(1) int contractVersion, @Min(1) int localSchemaVersion,
                                   @NotNull Instant createdAt, UUID lastAcknowledgedEventId,
                                   @NotNull List<@Valid PatientPayload> patients) {}

    public record SnapshotResponse(UUID snapshotId, int contractVersion, int localSchemaVersion,
                                   Instant snapshotCreatedAt, Instant uploadedAt, int patientCount,
                                   UUID lastAcknowledgedEventId, String sha256, long contentLength) {
        static SnapshotResponse from(BackupSnapshot snapshot) {
            return new SnapshotResponse(snapshot.getId(), snapshot.getContractVersion(), snapshot.getLocalSchemaVersion(),
                    snapshot.getSnapshotCreatedAt(), snapshot.getUploadedAt(), snapshot.getPatientCount(),
                    snapshot.getLastAcknowledgedEventId(), snapshot.getSha256(), snapshot.getContentLength());
        }
    }

    public record RecoveryRequest(@NotBlank @Pattern(regexp = "SUCCESS|FAILED") String outcome,
                                  @Size(max = 500) String details) {}
}
