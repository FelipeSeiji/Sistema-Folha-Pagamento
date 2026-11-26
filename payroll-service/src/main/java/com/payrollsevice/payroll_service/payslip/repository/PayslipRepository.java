package com.payrollsevice.payroll_service.payslip.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payrollsevice.payroll_service.payslip.model.Payslip;

import jakarta.persistence.LockModeType;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {
    List<Payslip> findByPayrollId(UUID payrollId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payslip> findForUpdateById(UUID id);

    @Query("""
        select count(p) as payslipCount, coalesce(sum(p.totalEarnings), 0) as totalEarnings,
               coalesce(sum(p.totalDeductions), 0) as totalDeductions, coalesce(sum(p.netPay), 0) as netPay
        from Payslip p
        where p.payroll.id = :payrollId
        """)
    PayslipTotals summarizeByPayrollId(@Param("payrollId") UUID payrollId);

    interface PayslipTotals {
        long getPayslipCount();
        BigDecimal getTotalEarnings();
        BigDecimal getTotalDeductions();
        BigDecimal getNetPay();
    }
}
