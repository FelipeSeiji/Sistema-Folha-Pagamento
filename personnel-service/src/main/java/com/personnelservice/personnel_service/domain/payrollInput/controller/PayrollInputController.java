package com.personnelservice.personnel_service.domain.payrollInput.controller;

import java.time.YearMonth;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.personnelservice.personnel_service.domain.payrollInput.dto.PayrollInputResponse;
import com.personnelservice.personnel_service.domain.payrollInput.service.PayrollInputService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/payroll-input")
@RequiredArgsConstructor
public class PayrollInputController {
    private final PayrollInputService service;

    @GetMapping
    public PayrollInputResponse find(
        @RequestParam("companyId") UUID companyId,
        @RequestParam("period") @DateTimeFormat(pattern = "uuuu-MM") YearMonth period
    ) {
        return service.find(companyId, period);
    }
}
