package com.personnelservice.personnel_service.domain.employmentContract.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import com.personnelservice.personnel_service.domain.employee.repository.EmployeeRepository;
import com.personnelservice.personnel_service.domain.company.repository.CompanyRepository;
import com.personnelservice.personnel_service.domain.department.repository.DepartmentRepository;
import com.personnelservice.personnel_service.domain.jobPosition.repository.JobPositionRepository;
import com.personnelservice.personnel_service.domain.employmentContract.dto.EmploymentContractDtos;
import com.personnelservice.personnel_service.domain.employmentContract.model.EmploymentContract;
import com.personnelservice.personnel_service.domain.employmentContract.repository.EmploymentContractRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class EmploymentContractService {
    private final EmploymentContractRepository employmentContractRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final JobPositionRepository jobPositionRepository;

    @Transactional
    public EmploymentContractDtos.Response create(@NotNull @Valid EmploymentContractDtos.Request request) {
        var employee = employeeRepository.findById(request.employeeId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found."));
        var company = companyRepository.findById(request.companyId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found."));
        var department = departmentRepository.findById(request.departmentId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found."));
        var position = jobPositionRepository.findById(request.positionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job position not found."));
        if (!company.getId().equals(department.getCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department does not belong to the company.");
        }

        EmploymentContract entity = new EmploymentContract();
        entity.setEmployee(employee);
        entity.setCompany(company);
        entity.setDepartment(department);
        entity.setPosition(position);
        entity.setRegistrationNumber(request.registrationNumber());
        entity.setHireDate(request.hireDate());
        entity.setMonthlyWorkHours(request.monthlyWorkHours());
        return EmploymentContractDtos.Response.from(employmentContractRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public EmploymentContractDtos.Response findById(UUID id) {
        return employmentContractRepository.findById(id)
            .map(EmploymentContractDtos.Response::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employment contract not found."));
    }
}
