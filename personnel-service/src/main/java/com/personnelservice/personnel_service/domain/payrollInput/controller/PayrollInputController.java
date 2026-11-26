package com.personnelservice.personnel_service.domain.payrollInput.controller;

import java.time.YearMonth;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.personnelservice.personnel_service.domain.employmentContract.service.EmploymentContractService;
import com.personnelservice.personnel_service.domain.payrollInput.dto.PayrollInputResponse;
import com.personnelservice.personnel_service.domain.payrollInput.service.PayrollInputService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PayrollInputController {
    private final PayrollInputService service;

    private final EmploymentContractService contracts;

    @GetMapping("/internal/payroll-input")
    public PayrollInputResponse find(
        @RequestParam("companyId") UUID companyId,
        @RequestParam("period") @DateTimeFormat(pattern = "uuuu-MM") YearMonth period
    ) {
        return service.find(companyId, period);
    }

    @GetMapping("/internal/employment-contract/{id}/company")
    public ContractCompany findContractCompany(@PathVariable("id") UUID id) {
        return new ContractCompany(id, contracts.findById(id).companyId());
    }

    public record ContractCompany(UUID id, UUID companyId) {
    }
}
