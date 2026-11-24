package com.personnelservice.personnel_service.domain.employmentContract.controller;

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

import com.personnelservice.personnel_service.domain.employmentContract.dto.EmploymentContractDtos;
import com.personnelservice.personnel_service.domain.employmentContract.service.EmploymentContractService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/employment-contract")
@RequiredArgsConstructor
public class EmploymentContractController {
    private final EmploymentContractService employmentContractService;

    @PostMapping
    public ResponseEntity<EmploymentContractDtos.Response> create(@Valid @RequestBody EmploymentContractDtos.Request request) {
        EmploymentContractDtos.Response employmentContract = employmentContractService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(employmentContract.id())
            .toUri();

        return ResponseEntity.created(location).body(employmentContract);
    }

    @GetMapping("/{id}")
    public EmploymentContractDtos.Response findById(@PathVariable("id") UUID id) {
        return employmentContractService.findById(id);
    }
}
