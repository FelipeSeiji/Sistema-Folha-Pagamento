package com.personnelservice.personnel_service.domain.salaryHistory.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.personnelservice.personnel_service.domain.salaryHistory.dto.SalaryHistoryDtos;
import com.personnelservice.personnel_service.domain.salaryHistory.service.SalaryHistoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/salary-history")
@RequiredArgsConstructor
public class SalaryHistoryController {
    private final SalaryHistoryService salaryHistoryService;

    @PostMapping
    public ResponseEntity<SalaryHistoryDtos.Response> create(@Valid @RequestBody SalaryHistoryDtos.Request request) {
        SalaryHistoryDtos.Response salaryHistory = salaryHistoryService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(salaryHistory.id())
            .toUri();

        return ResponseEntity.created(location).body(salaryHistory);
    }

    @PostMapping("/contract/{contractId}/change")
    public ResponseEntity<SalaryHistoryDtos.Response> change(
        @PathVariable("contractId") UUID contractId,
        @Valid @RequestBody SalaryHistoryDtos.ChangeRequest request
    ) {
        var history = salaryHistoryService.change(contractId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/salary-history/{id}")
            .buildAndExpand(history.id())
            .toUri();
        return ResponseEntity.created(location).body(history);
    }

    @GetMapping("/{id}")
    public SalaryHistoryDtos.Response findById(@PathVariable("id") UUID id) {
        return salaryHistoryService.findById(id);
    }
}
