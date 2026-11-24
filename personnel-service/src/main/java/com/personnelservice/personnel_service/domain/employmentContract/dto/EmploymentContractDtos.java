package com.personnelservice.personnel_service.domain.employmentContract.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmploymentContractDtos {
    public record Request(
        @NotNull UUID employeeId,
        @NotNull UUID companyId,
        @NotNull UUID departmentId,
        @NotNull UUID positionId,
        @NotBlank @Size(max = 50) String registrationNumber,
        @NotNull LocalDate hireDate,
        @NotNull @Positive Integer monthlyWorkHours
    ) {
    }

    public record Response(
        UUID id,
        UUID employeeId,
        UUID companyId,
        UUID departmentId,
        UUID positionId,
        String registrationNumber,
        LocalDate hireDate,
        LocalDate terminationDate,
        int monthlyWorkHours
    ) {
    }
}
