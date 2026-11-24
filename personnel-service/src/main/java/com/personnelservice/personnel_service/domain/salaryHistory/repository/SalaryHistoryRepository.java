package com.personnelservice.personnel_service.domain.salaryHistory.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personnelservice.personnel_service.domain.salaryHistory.model.SalaryHistory;

public interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, UUID> {
}
