package com.gt.sync;

import com.gt.security.DevicePrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {
    private final SyncService sync;

    public SyncController(SyncService sync) { this.sync = sync; }

    @PostMapping("/events")
    SyncDtos.BatchResponse events(@AuthenticationPrincipal DevicePrincipal principal,
                                  @Valid @RequestBody SyncDtos.BatchRequest request) {
        return sync.synchronize(principal, request);
    }
}
