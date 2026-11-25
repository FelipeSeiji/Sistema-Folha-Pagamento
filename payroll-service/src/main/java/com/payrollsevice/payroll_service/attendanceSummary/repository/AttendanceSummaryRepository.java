package com.payrollsevice.payroll_service.attendanceSummary.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payrollsevice.payroll_service.attendanceSummary.model.AttendanceSummary;

public interface AttendanceSummaryRepository extends JpaRepository<AttendanceSummary, UUID> {
    List<AttendanceSummary> findByPeriodAndContractIdIn(LocalDate period, List<UUID> contractIds);

    Optional<AttendanceSummary> findByContractIdAndPeriod(UUID contractId, LocalDate period);
}
