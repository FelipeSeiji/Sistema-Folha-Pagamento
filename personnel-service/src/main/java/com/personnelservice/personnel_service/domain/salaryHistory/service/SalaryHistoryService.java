package com.personnelservice.personnel_service.domain.salaryHistory.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import com.personnelservice.personnel_service.domain.employmentContract.repository.EmploymentContractRepository;
import com.personnelservice.personnel_service.domain.salaryHistory.dto.SalaryHistoryDtos;
import com.personnelservice.personnel_service.domain.salaryHistory.model.SalaryHistory;
import com.personnelservice.personnel_service.domain.salaryHistory.repository.SalaryHistoryRepository;

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
    public SalaryHistoryDtos.Response create(@NotNull @Valid SalaryHistoryDtos.Request request) {
        var contract = employmentContractRepository.findById(request.contractId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employment contract not found."));
        if (request.effectiveUntil() != null && request.effectiveUntil().isBefore(request.effectiveFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot precede the start date.");
        }

        SalaryHistory entity = new SalaryHistory();
        entity.setContract(contract);
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setEffectiveUntil(request.effectiveUntil());
        entity.setSalary(request.salary().setScale(2));
        return SalaryHistoryDtos.Response.from(salaryHistoryRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public SalaryHistoryDtos.Response findById(UUID id) {
        return salaryHistoryRepository.findById(id)
            .map(SalaryHistoryDtos.Response::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salary history not found."));
    }
}
