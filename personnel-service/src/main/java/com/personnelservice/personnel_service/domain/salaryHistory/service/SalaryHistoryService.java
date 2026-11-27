package com.personnelservice.personnel_service.domain.salaryHistory.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import com.personnelservice.personnel_service.domain.employmentContract.model.EmploymentContract;
import com.personnelservice.personnel_service.domain.employmentContract.repository.EmploymentContractRepository;
import com.personnelservice.personnel_service.domain.salaryHistory.dto.SalaryHistoryDtos;
import com.personnelservice.personnel_service.domain.salaryHistory.model.SalaryHistory;
import com.personnelservice.personnel_service.domain.salaryHistory.repository.SalaryHistoryRepository;
import com.personnelservice.personnel_service.security.PersonnelCompanyAccess;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class SalaryHistoryService {
    private final SalaryHistoryRepository salaryHistoryRepository;
    private final EmploymentContractRepository employmentContractRepository;

    @Transactional
    public SalaryHistoryDtos.Response create(Jwt principal, @NotNull @Valid SalaryHistoryDtos.Request request) {
        var contract = lockContract(request.contractId());
        PersonnelCompanyAccess.require(principal, contract.getCompany().getId());
        var start = request.effectiveFrom();
        var end = request.effectiveUntil();
        if (end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot precede the start date.");
        }
        requireContractPeriod(contract, start, end);
        boolean overlaps = salaryHistoryRepository.findByContractId(contract.getId()).stream()
            .anyMatch(period -> (end == null || !period.getEffectiveFrom().isAfter(end))
                && coversEnd(period, start));
        if (overlaps) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Salary periods cannot overlap.");
        }

        return savePeriod(contract, start, end, request.salary());
    }

    @Transactional
    public SalaryHistoryDtos.Response change(
        Jwt principal,
        UUID contractId,
        @NotNull @Valid SalaryHistoryDtos.ChangeRequest request
    ) {
        var contract = lockContract(contractId);
        PersonnelCompanyAccess.require(principal, contract.getCompany().getId());
        var start = request.effectiveFrom();
        var end = contract.getTerminationDate();
        requireContractPeriod(contract, start, end);

        var history = salaryHistoryRepository.findByContractId(contractId);
        if (history.stream().anyMatch(period -> !period.getEffectiveFrom().isBefore(start))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Salary changes must follow the latest start date.");
        }
        var previous = history.stream().filter(period -> coversEnd(period, start)).toList();
        if (previous.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Existing salary periods overlap.");
        }
        previous.forEach(period -> period.setEffectiveUntil(start.minusDays(1)));

        return savePeriod(contract, start, end, request.salary());
    }

    private EmploymentContract lockContract(UUID id) {
        return employmentContractRepository.findLockedById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employment contract not found."));
    }

    private void requireContractPeriod(EmploymentContract contract, LocalDate start, LocalDate end) {
        var termination = contract.getTerminationDate();
        if (start.isBefore(contract.getHireDate()) || (termination != null
                && (start.isAfter(termination) || end == null || end.isAfter(termination)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Salary period must be within the contract dates.");
        }
    }

    private boolean coversEnd(SalaryHistory period, LocalDate date) {
        return period.getEffectiveUntil() == null || !period.getEffectiveUntil().isBefore(date);
    }

    private SalaryHistoryDtos.Response savePeriod(
        EmploymentContract contract, LocalDate start, LocalDate end, BigDecimal salary
    ) {
        SalaryHistory entity = new SalaryHistory();
        entity.setContract(contract);
        entity.setEffectiveFrom(start);
        entity.setEffectiveUntil(end);
        entity.setSalary(salary.setScale(2));
        return SalaryHistoryDtos.Response.from(salaryHistoryRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public SalaryHistoryDtos.Response findById(Jwt principal, UUID id) {
        var history = salaryHistoryRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salary history not found."));
        PersonnelCompanyAccess.require(principal, history.getContract().getCompany().getId());
        return SalaryHistoryDtos.Response.from(history);
    }
}
