package com.personnelservice.personnel_service.domain.company.service;

import java.util.UUID;

import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import com.personnelservice.personnel_service.domain.company.dto.CompanyDtos;
import com.personnelservice.personnel_service.domain.company.model.Company;
import com.personnelservice.personnel_service.domain.company.repository.CompanyRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class CompanyService {
    private static final String CNPJ_UNIQUE_CONSTRAINT = "uk_company_cnpj";

    private final CompanyRepository companyRepository;

    @Transactional
    public CompanyDtos.Response create(@NotNull @Valid CompanyDtos.Request request) {
        Company company = new Company(null, request.cnpj(), request.legalName());
        try {
            return CompanyDtos.Response.from(companyRepository.saveAndFlush(company));
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMostSpecificCause() instanceof PSQLException violation
                    && "23505".equals(violation.getSQLState())
                    && violation.getServerErrorMessage() != null
                    && CNPJ_UNIQUE_CONSTRAINT.equals(violation.getServerErrorMessage().getConstraint())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "CNPJ is already registered.", exception);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Page<CompanyDtos.Response> list(Pageable pageable) {
        return companyRepository.findAll(pageable).map(CompanyDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public CompanyDtos.Response findById(UUID id) {
        return companyRepository.findById(id)
            .map(CompanyDtos.Response::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found."));
    }
}
