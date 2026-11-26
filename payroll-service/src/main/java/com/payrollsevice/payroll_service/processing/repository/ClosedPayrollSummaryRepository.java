package com.payrollsevice.payroll_service.processing.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payrollsevice.payroll_service.processing.model.ClosedPayrollSummary;

public interface ClosedPayrollSummaryRepository extends JpaRepository<ClosedPayrollSummary, UUID> {
    boolean existsByEventId(UUID eventId);
}
