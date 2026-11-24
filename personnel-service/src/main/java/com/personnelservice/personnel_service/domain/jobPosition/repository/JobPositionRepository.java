package com.personnelservice.personnel_service.domain.jobPosition.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.personnelservice.personnel_service.domain.jobPosition.model.JobPosition;

import jakarta.persistence.LockModeType;

public interface JobPositionRepository extends JpaRepository<JobPosition, UUID> {
    Page<JobPosition> findByActiveTrue(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<JobPosition> findLockedById(UUID id);
}
