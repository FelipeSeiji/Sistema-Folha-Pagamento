package com.personnelservice.personnel_service.domain.employmentContract.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personnelservice.personnel_service.domain.employmentContract.model.EmploymentContract;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, UUID> {
}
