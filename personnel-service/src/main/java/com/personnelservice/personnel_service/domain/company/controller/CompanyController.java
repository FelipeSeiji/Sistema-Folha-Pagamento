package com.personnelservice.personnel_service.domain.company.controller;

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

import com.personnelservice.personnel_service.domain.company.dto.CompanyDtos;
import com.personnelservice.personnel_service.domain.company.service.CompanyService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/company")
@RequiredArgsConstructor
public class CompanyController {
    private final CompanyService companyService;

    @PostMapping
    public ResponseEntity<CompanyDtos.Response> create(@Valid @RequestBody CompanyDtos.Request request) {
        CompanyDtos.Response company = companyService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(company.id())
            .toUri();

        return ResponseEntity.created(location).body(company);
    }

    @GetMapping("/{id}")
    public CompanyDtos.Response findById(@PathVariable("id") UUID id) {
        return companyService.findById(id);
    }
}
