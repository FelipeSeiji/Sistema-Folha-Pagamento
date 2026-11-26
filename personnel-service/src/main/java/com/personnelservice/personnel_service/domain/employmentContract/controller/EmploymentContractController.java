package com.personnelservice.personnel_service.domain.employmentContract.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.personnelservice.personnel_service.domain.employmentContract.dto.EmploymentContractDtos;
import com.personnelservice.personnel_service.domain.employmentContract.service.EmploymentContractService;

import com.personnelservice.personnel_service.security.PersonnelCompanyAccess;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/employment-contract")
@RequiredArgsConstructor
public class EmploymentContractController {
    private final EmploymentContractService employmentContractService;

    @PostMapping
    public ResponseEntity<EmploymentContractDtos.Response> create(
        @Valid @RequestBody EmploymentContractDtos.Request request, @AuthenticationPrincipal Jwt principal
    ) {
        EmploymentContractDtos.Response employmentContract = employmentContractService.create(request, principal);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(employmentContract.id())
            .toUri();

        return ResponseEntity.created(location).body(employmentContract);
    }

    @PostMapping("/{id}/termination")
    public EmploymentContractDtos.Response terminate(
        @PathVariable("id") UUID id,
        @Valid @RequestBody EmploymentContractDtos.TerminationRequest request,
        @AuthenticationPrincipal Jwt principal
    ) {
        return employmentContractService.terminate(id, request, principal);
    }

    @GetMapping
    public PagedModel<EmploymentContractDtos.Response> list(
        @AuthenticationPrincipal Jwt principal, @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return new PagedModel<>(employmentContractService.list(principal, pageable));
    }

    @GetMapping("/{id}")
    public EmploymentContractDtos.Response findById(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt principal) {
        var contract = employmentContractService.findById(id);
        PersonnelCompanyAccess.require(principal, contract.companyId());
        return contract;
    }
}
