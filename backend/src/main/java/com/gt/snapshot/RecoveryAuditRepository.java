package com.gt.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RecoveryAuditRepository extends JpaRepository<RecoveryAudit, UUID> {}
