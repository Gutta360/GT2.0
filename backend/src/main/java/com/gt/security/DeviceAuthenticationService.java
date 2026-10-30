package com.gt.security;

import com.gt.device.Device;
import com.gt.device.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class DeviceAuthenticationService {
    private final DeviceRepository devices;
    private final SecretHashing hashing;

    public DeviceAuthenticationService(DeviceRepository devices, SecretHashing hashing) {
        this.devices = devices;
        this.hashing = hashing;
    }

    @Transactional
    public Optional<DevicePrincipal> authenticate(String rawToken) {
        return devices.findByTokenHashAndStatus(hashing.sha256(rawToken), "ACTIVE")
                .filter(Device::isActive)
                .map(device -> {
                    device.touch(Instant.now());
                    return new DevicePrincipal(device.getTenant().getId(), device.getId(),
                            device.getTenant().getCode(), device.getName());
                });
    }
}
