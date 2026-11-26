package com.personnelservice.personnel_service.domain.employmentContract.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
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

import com.personnelservice.personnel_service.domain.salaryHistory.repository.SalaryHistoryRepository;

import com.personnelservice.personnel_service.security.PersonnelCompanyAccess;

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
    private final SalaryHistoryRepository salaryHistoryRepository;

    @Transactional
    public EmploymentContractDtos.Response create(@NotNull @Valid EmploymentContractDtos.Request request, Jwt principal) {
        PersonnelCompanyAccess.require(principal, request.companyId());
        var employee = employeeRepository.findLockedById(request.employeeId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found."));
        var company = companyRepository.findLockedById(request.companyId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found."));
        var department = departmentRepository.findLockedById(request.departmentId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found."));
        var position = jobPositionRepository.findLockedById(request.positionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job position not found."));
        if (!company.getId().equals(department.getCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department does not belong to the company.");
        }

        if (!employee.isActive() || !company.isActive() || !department.isActive() || !position.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employment contract requires active records.");
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

    @Transactional
    public EmploymentContractDtos.Response terminate(
        UUID id, @NotNull @Valid EmploymentContractDtos.TerminationRequest request, Jwt principal
    ) {
        var contract = employmentContractRepository.findLockedById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employment contract not found."));
        PersonnelCompanyAccess.require(principal, contract.getCompany().getId());
        var date = request.terminationDate();
        if (date.isBefore(contract.getHireDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Termination cannot precede the hire date.");
        }
        if (contract.getTerminationDate() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employment contract already has a termination date.");
        }

        var history = salaryHistoryRepository.findByContractId(id);
        if (history.stream().anyMatch(period -> period.getEffectiveFrom().isAfter(date))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Salary history contains periods after termination.");
        }
        for (var period : history) {
            if (period.getEffectiveUntil() == null || period.getEffectiveUntil().isAfter(date)) {
                period.setEffectiveUntil(date);
            }
        }
        contract.setTerminationDate(date);
        return EmploymentContractDtos.Response.from(contract);
    }

    @Transactional(readOnly = true)
    public Page<EmploymentContractDtos.Response> list(Jwt principal, Pageable pageable) {
        var companies = PersonnelCompanyAccess.companyIds(principal);
        if (companies.isEmpty()) {
            return Page.empty(pageable);
        }
        return employmentContractRepository.findByCompanyIdIn(companies, pageable)
            .map(EmploymentContractDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public EmploymentContractDtos.Response findById(UUID id) {
        return employmentContractRepository.findById(id)
            .map(EmploymentContractDtos.Response::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employment contract not found."));
    }
}
