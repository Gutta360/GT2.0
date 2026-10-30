package com.gt.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProcessedSyncEventRepository extends JpaRepository<ProcessedSyncEvent, UUID> {
    Optional<ProcessedSyncEvent> findByTenant_IdAndEventId(UUID tenantId, UUID eventId);
    Optional<ProcessedSyncEvent> findFirstByTenant_IdOrderByProcessedAtDesc(UUID tenantId);
}
