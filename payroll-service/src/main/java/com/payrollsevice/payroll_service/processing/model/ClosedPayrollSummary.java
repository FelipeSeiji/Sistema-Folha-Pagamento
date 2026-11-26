package com.payrollsevice.payroll_service.processing.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "TB_CLOSED_PAYROLL_SUMMARY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ClosedPayrollSummary {
    @Id
    private UUID payrollId;
    private UUID eventId;
    private UUID companyId;
    private LocalDate period;
    private Instant closedAt;
    private long payslipCount;
    private BigDecimal totalEarnings;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;
}
