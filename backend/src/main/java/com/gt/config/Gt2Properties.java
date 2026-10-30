package com.gt.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "gt2")
public record Gt2Properties(
        @NotBlank @Size(min = 32) String adminToken,
        @NotBlank String snapshotMasterKey,
        Path snapshotStorage,
        List<String> allowedOrigins,
        Duration enrollmentTtl,
        @Min(1) @Max(500) int maxBatchSize,
        @Min(1_024) long maxSnapshotBytes,
        @Min(1) @Max(100) int snapshotRetentionCount
) {
}
