package com.gt.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ProvisioningDtos {
    private ProvisioningDtos() {}

    public record CreateTenantRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{2,50}$") String tenantCode,
            @NotBlank @Size(min = 2, max = 120) String tenantName) {}

    public record TenantResponse(UUID tenantId, String tenantCode, String tenantName, String status, Instant createdAt) {}

    public record EnrollmentCodeResponse(UUID tenantId, String enrollmentCode, Instant expiresAt) {}

    public record RegisterDeviceRequest(
            @NotBlank @Size(min = 20, max = 200) String enrollmentCode,
            @NotBlank @Size(min = 2, max = 120) String deviceName,
            @NotBlank @Size(max = 120) String applicationId,
            @NotBlank @Size(max = 40) String applicationVersion,
            @NotBlank @Pattern(regexp = "ANDROID") String platform) {}

    public record RegisterDeviceResponse(
            UUID tenantId, UUID deviceId, String tokenType, String deviceToken,
            String tenantCode, String tenantName, Instant registeredAt) {}

    public record RevokeDeviceResponse(UUID deviceId, String status, Instant revokedAt) {}
}
