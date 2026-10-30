package com.gt.security;

import java.util.UUID;

public record DevicePrincipal(UUID tenantId, UUID deviceId, String tenantCode, String deviceName) {
}
