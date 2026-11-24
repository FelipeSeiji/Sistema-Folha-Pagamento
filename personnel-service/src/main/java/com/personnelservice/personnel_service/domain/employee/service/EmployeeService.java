package com.personnelservice.personnel_service.domain.employee.service;

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

import com.personnelservice.personnel_service.domain.employee.dto.EmployeeDtos;
import com.personnelservice.personnel_service.domain.employee.model.Employee;
import com.personnelservice.personnel_service.domain.employee.repository.EmployeeRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class EmployeeService {
    private static final String CPF_UNIQUE_CONSTRAINT = "tb_employee_cpf_key";

    private final EmployeeRepository employeeRepository;

    @Transactional
    public EmployeeDtos.Response create(@NotNull @Valid EmployeeDtos.Request request) {
        Employee employee = new Employee(null, request.cpf(), request.name(), request.email());
        try {
            return EmployeeDtos.Response.from(employeeRepository.saveAndFlush(employee));
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMostSpecificCause() instanceof PSQLException violation
                    && "23505".equals(violation.getSQLState())
                    && violation.getServerErrorMessage() != null
                    && CPF_UNIQUE_CONSTRAINT.equals(violation.getServerErrorMessage().getConstraint())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF is already registered.", exception);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Page<EmployeeDtos.Response> list(Pageable pageable) {
        return employeeRepository.findAll(pageable).map(EmployeeDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public EmployeeDtos.Response findById(UUID id) {
        return employeeRepository.findById(id)
            .map(EmployeeDtos.Response::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found."));
    }
}
