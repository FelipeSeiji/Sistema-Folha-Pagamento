package com.payrollsevice.payroll_service.rubric.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.payrollsevice.payroll_service.rubric.dto.RubricDtos;
import com.payrollsevice.payroll_service.rubric.model.Rubric;
import com.payrollsevice.payroll_service.rubric.repository.RubricRepository;

import com.payrollsevice.payroll_service.security.PayrollCompanyAccess;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RubricService {
    private final RubricRepository repository;


    @Transactional
    public RubricDtos.Response create(RubricDtos.Request request, Jwt principal) {
        PayrollCompanyAccess.require(principal, request.companyId());
        Rubric entity = new Rubric();
        entity.setCompanyId(request.companyId());
        applyChanges(entity, request);
        return RubricDtos.Response.from(repository.saveAndFlush(entity));
    }

    @Transactional
    public RubricDtos.Response update(UUID id, RubricDtos.Request request, Jwt principal) {
        Rubric entity = requireCompanyRubric(id, principal);
        if (!entity.getCompanyId().equals(request.companyId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rubric company cannot be changed.");
        }
        applyChanges(entity, request);
        repository.flush();
        return RubricDtos.Response.from(entity);
    }

    @Transactional
    public void delete(UUID id, Jwt principal) {
        repository.delete(requireCompanyRubric(id, principal));
    }

    @Transactional(readOnly = true)
    public Page<RubricDtos.Response> list(Pageable pageable, Jwt principal) {
        var companies = PayrollCompanyAccess.companyIds(principal);
        if (companies.isEmpty()) {
            return Page.empty(pageable);
        }
        return repository.findByCompanyIdIn(companies, pageable).map(RubricDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public RubricDtos.Response findById(UUID id, Jwt principal) {
        return RubricDtos.Response.from(requireCompanyRubric(id, principal));
    }

    private Rubric requireCompanyRubric(UUID id, Jwt principal) {
        Rubric rubric = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rubric not found."));
        PayrollCompanyAccess.require(principal, rubric.getCompanyId());
        return rubric;
    }

    private static void applyChanges(Rubric entity, RubricDtos.Request request) {
        entity.setCode(request.code().strip());
        entity.setDescription(request.description().strip());
        entity.setType(request.type());
        entity.setSubjectToInss(request.subjectToInss());
        entity.setSubjectToIrrf(request.subjectToIrrf());
        entity.setSubjectToFgts(request.subjectToFgts());
    }
}
