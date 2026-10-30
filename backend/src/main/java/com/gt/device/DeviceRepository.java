package com.gt.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    Optional<Device> findByTokenHashAndStatus(String tokenHash, String status);
    Optional<Device> findByIdAndTenant_Id(UUID id, UUID tenantId);
}
