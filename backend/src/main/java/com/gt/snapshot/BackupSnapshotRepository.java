package com.gt.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BackupSnapshotRepository extends JpaRepository<BackupSnapshot, UUID> {
    Optional<BackupSnapshot> findByIdAndTenant_IdAndStatus(UUID id, UUID tenantId, String status);
    List<BackupSnapshot> findAllByTenant_IdAndStatusOrderByUploadedAtDesc(UUID tenantId, String status);
}
