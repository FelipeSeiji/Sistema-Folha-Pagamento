package com.payrollsevice.payroll_service.processing.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.payrollsevice.payroll_service.personnel.PersonnelClient;
import com.payrollsevice.payroll_service.personnel.PersonnelInputConflictException;
import com.payrollsevice.payroll_service.personnel.PersonnelUnavailableException;
import com.payrollsevice.payroll_service.personnel.dto.PersonnelPayrollInput;
import com.payrollsevice.payroll_service.processing.dto.PayrollDtos;
import com.payrollsevice.payroll_service.processing.model.Payroll;
import com.payrollsevice.payroll_service.processing.model.Payroll.Snapshot;
import com.payrollsevice.payroll_service.processing.repository.PayrollRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayrollService {
    private final PayrollRepository repository;
    private final PersonnelClient personnelClient;

    public PayrollDtos.Response create(PayrollDtos.CreateRequest request) {
        LocalDate period = request.period().atDay(1);
        if (repository.existsByCompanyIdAndPeriodAndType(request.companyId(), period, request.type())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payroll already exists for this company, period and type.");
        }

        PersonnelPayrollInput input;
        try {
            input = personnelClient.getPayrollInputs(request.companyId(), request.period());
        } catch (PersonnelInputConflictException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (PersonnelUnavailableException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Personnel inputs are unavailable.", exception);
        }

        Payroll payroll = new Payroll();
        payroll.setCompanyId(request.companyId());
        payroll.setPeriod(period);
        payroll.setType(request.type());
        payroll.setSnapshotCapturedAt(Instant.now());
        payroll.setSnapshotPayload(new Snapshot(input.contracts().stream()
            .map(PayrollService::snapshotContract)
            .toList()));

        try {
            return PayrollDtos.Response.from(repository.saveAndFlush(payroll));
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payroll already exists for this company, period and type.", exception);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Page<PayrollDtos.Response> list(Pageable pageable) {
        return repository.findAll(pageable).map(PayrollDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public PayrollDtos.Response findById(UUID id) {
        return PayrollDtos.Response.from(requirePayroll(id));
    }

    private Payroll requirePayroll(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll not found."));
    }

    private static Snapshot.Contract snapshotContract(PersonnelPayrollInput.Contract contract) {
        var salaryPeriods = contract.salaryPeriods().stream()
            .map(salary -> new Snapshot.SalaryPeriod(salary.from(), salary.until(), salary.salary()))
            .toList();

        return new Snapshot.Contract(contract.contractId(), contract.employeeId(), contract.employeeName(),
            contract.registrationNumber(), contract.hireDate(), contract.terminationDate(), contract.periodStart(),
            contract.periodEnd(), contract.monthlyWorkHours(), salaryPeriods);
    }
}
