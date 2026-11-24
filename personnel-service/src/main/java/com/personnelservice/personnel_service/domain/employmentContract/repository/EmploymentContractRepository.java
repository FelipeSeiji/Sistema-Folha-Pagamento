package com.personnelservice.personnel_service.domain.employmentContract.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.personnelservice.personnel_service.domain.employmentContract.model.EmploymentContract;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmploymentContract> findLockedById(UUID id);
}
