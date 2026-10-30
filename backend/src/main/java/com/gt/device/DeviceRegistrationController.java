package com.gt.device;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.gt.device.ProvisioningDtos.RegisterDeviceRequest;
import static com.gt.device.ProvisioningDtos.RegisterDeviceResponse;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceRegistrationController {
    private final ProvisioningService provisioning;

    public DeviceRegistrationController(ProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisterDeviceResponse register(@Valid @RequestBody RegisterDeviceRequest request) {
        return provisioning.register(request);
    }
}
