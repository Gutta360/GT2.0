package com.gt.device;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.gt.device.ProvisioningDtos.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminProvisioningController {
    private final ProvisioningService provisioning;

    public AdminProvisioningController(ProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    TenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return provisioning.createTenant(request);
    }

    @PostMapping("/tenants/{tenantId}/enrollment-codes")
    @ResponseStatus(HttpStatus.CREATED)
    EnrollmentCodeResponse createEnrollmentCode(@PathVariable UUID tenantId) {
        return provisioning.createEnrollmentCode(tenantId);
    }

    @PostMapping("/devices/{deviceId}/revoke")
    RevokeDeviceResponse revoke(@PathVariable UUID deviceId) {
        return provisioning.revoke(deviceId);
    }
}
