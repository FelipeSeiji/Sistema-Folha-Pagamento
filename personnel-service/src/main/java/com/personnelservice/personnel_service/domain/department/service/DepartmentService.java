package com.personnelservice.personnel_service.domain.department.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        Company company = companyRepository.findLockedById(request.companyId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found."));
        if (!company.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Company is inactive.");
        }
        Department department = departmentRepository.save(new Department(null, request.name(), company, true));
        return DepartmentDtos.Response.from(department);
    }

    @Transactional(readOnly = true)
    public Page<DepartmentDtos.Response> list(UUID companyId, Pageable pageable) {
        var departments = companyId == null
            ? departmentRepository.findByActiveTrue(pageable)
            : departmentRepository.findByCompanyIdAndActiveTrue(companyId, pageable);
        return departments.map(DepartmentDtos.Response::from);
    }

    @Transactional
    public DepartmentDtos.Response update(UUID id, DepartmentDtos.UpdateRequest request) {
        Department department = lockDepartment(id);
        if (!department.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inactive records cannot be updated.");
        }
        department.setName(request.name());
        return DepartmentDtos.Response.from(department);
    }

    @Transactional
    public void deactivate(UUID id) {
        lockDepartment(id).setActive(false);
    }

    private Department lockDepartment(UUID id) {
        return departmentRepository.findLockedById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found."));
    }

    @Transactional(readOnly = true)
    public DepartmentDtos.Response findById(UUID id) {
        return DepartmentDtos.Response.from(requireDepartment(id));
    }

    private Department requireDepartment(UUID id) {
        return departmentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found."));
    }
}
