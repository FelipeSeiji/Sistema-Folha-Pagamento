package com.payrollsevice.payroll_service.attendanceSummary.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payrollsevice.payroll_service.attendanceSummary.model.AttendanceSummary;

public interface AttendanceSummaryRepository extends JpaRepository<AttendanceSummary, UUID> {
    Optional<AttendanceSummary> findByContractIdAndPeriod(UUID contractId, LocalDate period);
}
