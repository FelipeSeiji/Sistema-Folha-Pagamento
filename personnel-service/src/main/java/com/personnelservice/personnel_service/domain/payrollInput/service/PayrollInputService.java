package com.personnelservice.personnel_service.domain.payrollInput.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.personnelservice.personnel_service.domain.company.repository.CompanyRepository;
import com.personnelservice.personnel_service.domain.employmentContract.model.EmploymentContract;
import com.personnelservice.personnel_service.domain.employmentContract.repository.EmploymentContractRepository;
import com.personnelservice.personnel_service.domain.payrollInput.dto.PayrollInputResponse;
import com.personnelservice.personnel_service.domain.salaryHistory.model.SalaryHistory;
import com.personnelservice.personnel_service.domain.salaryHistory.repository.SalaryHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayrollInputService {
    private final CompanyRepository companyRepository;
    private final EmploymentContractRepository contractRepository;
    private final SalaryHistoryRepository salaryRepository;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PayrollInputResponse find(UUID companyId, YearMonth period) {
        if (!companyRepository.existsById(companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found.");
        }

        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();
        var contracts = contractRepository.findOverlappingPeriod(companyId, start, end);
        if (contracts.isEmpty()) {
            return new PayrollInputResponse(companyId, period, List.of());
        }

        var salaries = salaryRepository.findOverlappingPeriod(companyId, start, end).stream()
            .collect(Collectors.groupingBy(salary -> salary.getContract().getId()));
        return new PayrollInputResponse(companyId, period, contracts.stream()
            .map(contract -> toInput(contract, start, end, salaries.getOrDefault(contract.getId(), List.of())))
            .toList());
    }

    private PayrollInputResponse.Contract toInput(
        EmploymentContract contract, LocalDate monthStart, LocalDate monthEnd, List<SalaryHistory> history
    ) {
        LocalDate start = later(monthStart, contract.getHireDate());
        LocalDate end = earlier(monthEnd, contract.getTerminationDate());
        LocalDate next = start;
        var salaries = new ArrayList<PayrollInputResponse.SalaryPeriod>();
        for (SalaryHistory salary : history) {
            LocalDate from = later(start, salary.getEffectiveFrom());
            LocalDate until = earlier(end, salary.getEffectiveUntil());
            if (until.isBefore(from)) {
                continue;
            }
            if (!from.equals(next)) {
                throw inconsistentSalary(contract, from.isBefore(next) ? "overlapping periods" : "missing coverage");
            }
            salaries.add(new PayrollInputResponse.SalaryPeriod(from, until, salary.getSalary()));
            next = until.plusDays(1);
        }
        if (!next.isAfter(end)) {
            throw inconsistentSalary(contract, "missing coverage");
        }

        return new PayrollInputResponse.Contract(contract.getId(), contract.getEmployee().getId(),
            contract.getEmployee().getName(), contract.getRegistrationNumber(), contract.getHireDate(),
            contract.getTerminationDate(), start, end, contract.getMonthlyWorkHours(), List.copyOf(salaries));
    }

    private static ResponseStatusException inconsistentSalary(EmploymentContract contract, String cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
            "Invalid salary history for contract " + contract.getId() + ": " + cause + ".");
    }

    private static LocalDate later(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private static LocalDate earlier(LocalDate first, LocalDate second) {
        return second == null || first.isBefore(second) ? first : second;
    }
}
