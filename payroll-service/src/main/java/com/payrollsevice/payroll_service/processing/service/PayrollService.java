package com.payrollsevice.payroll_service.processing.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.payrollsevice.payroll_service.outbox.model.PayrollOutboxEvent;
import com.payrollsevice.payroll_service.outbox.repository.PayrollOutboxRepository;
import com.payrollsevice.payroll_service.payslip.repository.PayslipRepository;
import com.payrollsevice.payroll_service.personnel.PersonnelClient;
import com.payrollsevice.payroll_service.personnel.PersonnelInputConflictException;
import com.payrollsevice.payroll_service.personnel.PersonnelUnavailableException;
import com.payrollsevice.payroll_service.personnel.dto.PersonnelPayrollInput;
import com.payrollsevice.payroll_service.processing.dto.PayrollDtos;
import com.payrollsevice.payroll_service.processing.model.Payroll;
import com.payrollsevice.payroll_service.processing.model.Payroll.Snapshot;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.processing.repository.ClosedPayrollSummaryRepository;
import com.payrollsevice.payroll_service.processing.repository.PayrollRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayrollService {
    private final PayrollRepository repository;
    private final PersonnelClient personnelClient;
    private final PayrollOutboxRepository outboxRepository;
    private final ClosedPayrollSummaryRepository summaryRepository;
    private final PayslipRepository payslipRepository;

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

    @Transactional
    public void delete(UUID id) {
        Payroll payroll = requirePayroll(id);
        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft payrolls can be deleted.");
        }
        repository.delete(payroll);
    }

    @Transactional(readOnly = true)
    public Page<PayrollDtos.Response> list(Pageable pageable) {
        return repository.findAll(pageable).map(PayrollDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public PayrollDtos.Response findById(UUID id) {
        return PayrollDtos.Response.from(requirePayroll(id));
    }

    @Transactional
    public PayrollDtos.Response close(UUID id) {
        Payroll payroll = repository.findForUpdateById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll not found."));
        if (payroll.getStatus() != PayrollStatus.CALCULATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only calculated payrolls can be closed.");
        }

        payroll.setStatus(PayrollStatus.CLOSED);
        outboxRepository.saveAndFlush(new PayrollOutboxEvent(id, payroll.getCompanyId(),
            YearMonth.from(payroll.getPeriod()), Instant.now()));
        return PayrollDtos.Response.from(payroll);
    }

    @Transactional(readOnly = true)
    public Optional<PayrollDtos.SummaryResponse> findSummary(UUID id) {
        Payroll payroll = requirePayroll(id);
        if (payroll.getStatus() != PayrollStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payroll must be closed to have a summary.");
        }

        return summaryRepository.findById(id).map(PayrollDtos.SummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public PayrollDtos.FinanceReport findFinanceReport(UUID id, Jwt principal) {
        Payroll payroll = requirePayroll(id);
        if (!(principal.getClaims().get("company_ids") instanceof Collection<?> companyIds)
                || !companyIds.contains(payroll.getCompanyId().toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access to this payroll company is required.");
        }
        if (payroll.getStatus() != PayrollStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payroll must be closed to have a finance report.");
        }

        var contracts = payroll.getSnapshotPayload().contracts().stream()
            .collect(Collectors.toMap(Snapshot.Contract::contractId, Function.identity()));
        var entries = payslipRepository.findByPayrollId(id).stream().map(payslip -> {
            var contract = contracts.get(payslip.getContractId());
            if (contract == null) {
                throw new IllegalStateException("Payslip contract is missing from the payroll snapshot.");
            }
            return new PayrollDtos.FinanceReportEntry(contract.employeeName(), contract.registrationNumber(),
                payslip.getNetPay());
        }).sorted(Comparator.comparing(PayrollDtos.FinanceReportEntry::employeeName)
            .thenComparing(PayrollDtos.FinanceReportEntry::registrationNumber)).toList();

        BigDecimal total = entries.stream().map(PayrollDtos.FinanceReportEntry::netPay)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayrollDtos.FinanceReport(YearMonth.from(payroll.getPeriod()), entries, total);
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
