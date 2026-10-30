package com.gt.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SyncBatchRepository extends JpaRepository<SyncBatch, UUID> {
    Optional<SyncBatch> findByIdAndTenant_Id(UUID id, UUID tenantId);
}
