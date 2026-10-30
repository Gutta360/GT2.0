package com.gt.snapshot;

import com.gt.security.DevicePrincipal;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/snapshots")
public class SnapshotController {
    private final SnapshotService service;

    public SnapshotController(SnapshotService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SnapshotDtos.SnapshotResponse upload(@AuthenticationPrincipal DevicePrincipal principal,
                                         @Valid @RequestBody SnapshotDtos.UploadRequest request) {
        return service.upload(principal, request);
    }

    @GetMapping
    List<SnapshotDtos.SnapshotResponse> list(@AuthenticationPrincipal DevicePrincipal principal) {
        return service.list(principal);
    }

    @GetMapping("/{snapshotId}")
    ResponseEntity<byte[]> download(@AuthenticationPrincipal DevicePrincipal principal,
                                    @PathVariable UUID snapshotId) {
        SnapshotService.Download download = service.download(principal, snapshotId);
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .header("X-Snapshot-Id", snapshotId.toString())
                .header("X-Snapshot-Sha256", download.sha256())
                .body(download.content());
    }

    @GetMapping("/{snapshotId}/recovery-package")
    ResponseEntity<byte[]> recoveryPackage(@AuthenticationPrincipal DevicePrincipal principal,
                                           @PathVariable UUID snapshotId) {
        SnapshotService.Download download = service.recoveryPackage(principal, snapshotId);
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .header("X-Snapshot-Id", snapshotId.toString())
                .header("X-Snapshot-Sha256", download.sha256())
                .body(download.content());
    }

    @PostMapping("/{snapshotId}/recovery-audit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void recovery(@AuthenticationPrincipal DevicePrincipal principal, @PathVariable UUID snapshotId,
                  @Valid @RequestBody SnapshotDtos.RecoveryRequest request) {
        service.recordRecovery(principal, snapshotId, request);
    }
}
