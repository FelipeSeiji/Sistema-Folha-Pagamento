package com.payrollsevice.payroll_service.attendanceSummary.dto;

import java.util.UUID;

import com.payrollsevice.payroll_service.attendanceSummary.model.AttendanceSummary;

import java.time.YearMonth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AttendanceSummaryDtos {
    public record Request(
        @NotNull UUID contractId, @NotNull YearMonth period,
        @NotNull @PositiveOrZero Long workedMinutes,
        @NotNull @PositiveOrZero Long overtimeMinutes,
        @NotNull @PositiveOrZero Long absenceMinutes
    ) {
    }

    public record Response(
        UUID id, UUID contractId, YearMonth period, long workedMinutes, long overtimeMinutes, long absenceMinutes
    ) {
        public static Response from(AttendanceSummary entity) {
            return new Response(entity.getId(), entity.getContractId(), YearMonth.from(entity.getPeriod()),
                entity.getWorkedMinutes(), entity.getOvertimeMinutes(), entity.getAbsenceMinutes());
        }
    }
}
