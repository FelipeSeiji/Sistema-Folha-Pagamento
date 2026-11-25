package com.payrollsevice.payroll_service.processing.dto;

import java.time.YearMonth;
import java.util.UUID;

import com.payrollsevice.payroll_service.processing.model.Payroll;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.processing.model.PayrollType;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PayrollDtos {
    public record CreateRequest(@NotNull UUID companyId, @NotNull YearMonth period, @NotNull PayrollType type) {
    }

    public record Response(UUID id, UUID companyId, YearMonth period, PayrollType type, PayrollStatus status) {
        public static Response from(Payroll payroll) {
            return new Response(payroll.getId(), payroll.getCompanyId(), YearMonth.from(payroll.getPeriod()),
                payroll.getType(), payroll.getStatus());
        }
    }
}
