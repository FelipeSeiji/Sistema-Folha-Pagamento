package com.payrollsevice.payroll_service.processing.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.payrollsevice.payroll_service.processing.model.Payroll;
import com.payrollsevice.payroll_service.processing.model.PayrollType;

import jakarta.persistence.LockModeType;

public interface PayrollRepository extends JpaRepository<Payroll, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payroll> findForUpdateById(UUID id);

    boolean existsByCompanyIdAndPeriodAndType(UUID companyId, LocalDate period, PayrollType type);
}
