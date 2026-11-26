package com.payrollsevice.payroll_service.processing.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.payrollsevice.payroll_service.processing.model.ClosedPayrollSummary;
import com.payrollsevice.payroll_service.processing.model.Payroll;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.processing.model.PayrollType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PayrollDtos {
    public record CreateRequest(@NotNull UUID companyId, @NotNull YearMonth period, @NotNull PayrollType type) {
    }

    public record CalculationRequest(
        @NotNull UUID salaryRubricId,
        @NotNull UUID overtimeRubricId,
        @NotNull UUID absenceRubricId,
        @NotNull @DecimalMin("1") @Digits(integer = 3, fraction = 4) BigDecimal overtimeMultiplier
    ) {
    }

    public record FinanceReport(YearMonth period, List<FinanceReportEntry> entries, BigDecimal totalNetPay) {
    }

    public record FinanceReportEntry(String employeeName, String registrationNumber, BigDecimal netPay) {
    }

    public record SummaryResponse(
        YearMonth period, long payslipCount, BigDecimal totalEarnings, BigDecimal totalDeductions, BigDecimal netPay
    ) {
        public static SummaryResponse from(ClosedPayrollSummary summary) {
            return new SummaryResponse(YearMonth.from(summary.getPeriod()), summary.getPayslipCount(),
                summary.getTotalEarnings(), summary.getTotalDeductions(), summary.getNetPay());
        }
    }

    public record Response(UUID id, UUID companyId, YearMonth period, PayrollType type, PayrollStatus status) {
        public static Response from(Payroll payroll) {
            return new Response(payroll.getId(), payroll.getCompanyId(), YearMonth.from(payroll.getPeriod()),
                payroll.getType(), payroll.getStatus());
        }
    }
}
