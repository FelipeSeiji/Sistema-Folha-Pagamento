package com.personnelservice.personnel_service.domain.jobPosition.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personnelservice.personnel_service.domain.jobPosition.model.JobPosition;

public interface JobPositionRepository extends JpaRepository<JobPosition, UUID> {
}
