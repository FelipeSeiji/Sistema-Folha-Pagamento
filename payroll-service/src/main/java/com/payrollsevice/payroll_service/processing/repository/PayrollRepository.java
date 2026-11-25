package com.payrollsevice.payroll_service.processing.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payrollsevice.payroll_service.processing.model.Payroll;
import com.payrollsevice.payroll_service.processing.model.PayrollType;

public interface PayrollRepository extends JpaRepository<Payroll, UUID> {
    Optional<Payroll> findByCompanyIdAndPeriodAndType(UUID companyId, LocalDate period, PayrollType type);
}
