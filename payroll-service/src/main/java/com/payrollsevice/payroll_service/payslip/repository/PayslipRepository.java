package com.payrollsevice.payroll_service.payslip.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payrollsevice.payroll_service.payslip.model.Payslip;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {
    List<Payslip> findByPayrollId(UUID payrollId);
}
