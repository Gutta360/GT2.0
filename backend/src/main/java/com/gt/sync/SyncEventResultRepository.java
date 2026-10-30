package com.gt.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SyncEventResultRepository extends JpaRepository<SyncEventResult, UUID> {
    List<SyncEventResult> findAllByBatch_IdOrderByEventIndex(UUID batchId);
}
