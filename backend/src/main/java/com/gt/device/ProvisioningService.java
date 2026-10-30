package com.gt.device;

import com.gt.config.ApiException;
import com.gt.config.Gt2Properties;
import com.gt.security.SecretHashing;
import com.gt.tenant.Tenant;
import com.gt.tenant.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static com.gt.device.ProvisioningDtos.*;

@Service
public class ProvisioningService {
    private final TenantRepository tenants;
    private final EnrollmentCodeRepository enrollmentCodes;
    private final DeviceRepository devices;
    private final SecretHashing hashing;
    private final Gt2Properties properties;

    public ProvisioningService(TenantRepository tenants, EnrollmentCodeRepository enrollmentCodes,
                               DeviceRepository devices, SecretHashing hashing, Gt2Properties properties) {
        this.tenants = tenants;
        this.enrollmentCodes = enrollmentCodes;
        this.devices = devices;
        this.hashing = hashing;
        this.properties = properties;
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        String code = request.tenantCode().trim().toUpperCase(Locale.ROOT);
        if (tenants.findByCode(code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "TENANT_CODE_EXISTS", "Tenant code already exists.");
        }
        Instant now = Instant.now();
        Tenant tenant = tenants.save(new Tenant(UUID.randomUUID(), code, request.tenantName().trim(), now));
        return new TenantResponse(tenant.getId(), tenant.getCode(), tenant.getName(), tenant.getStatus(), tenant.getCreatedAt());
    }

    @Transactional
    public EnrollmentCodeResponse createEnrollmentCode(UUID tenantId) {
        Tenant tenant = tenants.findById(tenantId)
                .filter(Tenant::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Active tenant was not found."));
        String rawCode = hashing.randomToken("GT2E_");
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.enrollmentTtl());
        enrollmentCodes.save(new EnrollmentCode(UUID.randomUUID(), tenant, hashing.sha256(rawCode), expiresAt, now));
        return new EnrollmentCodeResponse(tenantId, rawCode, expiresAt);
    }

    @Transactional
    public RegisterDeviceResponse register(RegisterDeviceRequest request) {
        if (!"com.gutta.gt2".equals(request.applicationId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APPLICATION_NOT_ALLOWED", "The application ID is not allowed.");
        }
        Instant now = Instant.now();
        EnrollmentCode enrollment = enrollmentCodes.findByCodeHash(hashing.sha256(request.enrollmentCode().trim()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_ENROLLMENT_CODE", "Enrollment code is invalid or expired."));
        if (!enrollment.canUseAt(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_ENROLLMENT_CODE", "Enrollment code is invalid or expired.");
        }
        enrollment.consume(now);
        String rawToken = hashing.randomToken("GT2D_");
        Device device = devices.save(new Device(UUID.randomUUID(), enrollment.getTenant(), request.deviceName().trim(),
                hashing.sha256(rawToken), now));
        return new RegisterDeviceResponse(enrollment.getTenant().getId(), device.getId(), "Bearer", rawToken,
                enrollment.getTenant().getCode(), enrollment.getTenant().getName(), now);
    }

    @Transactional
    public RevokeDeviceResponse revoke(UUID deviceId) {
        Device device = devices.findById(deviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "Device was not found."));
        Instant now = Instant.now();
        device.revoke(now);
        return new RevokeDeviceResponse(deviceId, device.getStatus(), now);
    }
}
