package com.payrollsevice.payroll_service.rubric.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.payrollsevice.payroll_service.rubric.dto.RubricDtos;
import com.payrollsevice.payroll_service.rubric.model.Rubric;
import com.payrollsevice.payroll_service.rubric.repository.RubricRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RubricService {
    private final RubricRepository repository;


    @Transactional
    public RubricDtos.Response create(RubricDtos.Request request) {
        Rubric entity = new Rubric();
        entity.setCompanyId(request.companyId());
        entity.setCode(request.code().strip());
        entity.setDescription(request.description().strip());
        entity.setType(request.type());
        entity.setSubjectToInss(request.subjectToInss());
        entity.setSubjectToIrrf(request.subjectToIrrf());
        entity.setSubjectToFgts(request.subjectToFgts());
        return RubricDtos.Response.from(repository.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public Page<RubricDtos.Response> list(Pageable pageable) {
        return repository.findAll(pageable).map(RubricDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public RubricDtos.Response findById(UUID id) {
        return repository.findById(id)
            .map(RubricDtos.Response::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rubric not found."));
    }
}
