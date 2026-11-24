package com.personnelservice.personnel_service.domain.salaryHistory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SalaryHistoryDtos {
    public record Request(
        @NotNull UUID contractId,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal salary
    ) {
    }

    public record Response(
        UUID id,
        UUID contractId,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        BigDecimal salary
    ) {
    }
}
