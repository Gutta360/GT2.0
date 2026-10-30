package com.gt.patient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByIdAndTenant_Id(UUID id, UUID tenantId);
    List<Patient> findAllByTenant_IdOrderByPatientNumber(UUID tenantId);
    long countByTenant_Id(UUID tenantId);
}
