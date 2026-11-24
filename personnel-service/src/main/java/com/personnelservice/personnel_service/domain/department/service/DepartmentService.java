package com.personnelservice.personnel_service.domain.department.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.personnelservice.personnel_service.domain.company.model.Company;
import com.personnelservice.personnel_service.domain.company.repository.CompanyRepository;
import com.personnelservice.personnel_service.domain.department.dto.DepartmentDtos;
import com.personnelservice.personnel_service.domain.department.model.Department;
import com.personnelservice.personnel_service.domain.department.repository.DepartmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public DepartmentDtos.Response create(DepartmentDtos.Request request) {
        Company company = companyRepository.findById(request.companyId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found."));
        Department department = departmentRepository.save(new Department(null, request.name(), company));
        return DepartmentDtos.Response.from(department);
    }

    @Transactional(readOnly = true)
    public DepartmentDtos.Response findById(UUID id) {
        return departmentRepository.findById(id)
            .map(DepartmentDtos.Response::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found."));
    }
}
