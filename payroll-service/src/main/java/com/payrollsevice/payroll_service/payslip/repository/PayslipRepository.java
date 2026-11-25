package com.payrollsevice.payroll_service.payslip.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.payrollsevice.payroll_service.payslip.model.Payslip;

import jakarta.persistence.LockModeType;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {
    List<Payslip> findByPayrollId(UUID payrollId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payslip> findForUpdateById(UUID id);
}
