package com.payrollsevice.payroll_service.personnel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record PersonnelPayrollInput(UUID companyId, YearMonth period, List<Contract> contracts) {
    public record Contract(
        UUID contractId, UUID employeeId, String employeeName, String registrationNumber,
        LocalDate hireDate, LocalDate terminationDate, LocalDate periodStart, LocalDate periodEnd,
        int monthlyWorkHours, List<SalaryPeriod> salaryPeriods
    ) {
    }

    public record SalaryPeriod(LocalDate from, LocalDate until, BigDecimal salary) {
    }
}
