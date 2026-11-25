package com.payrollsevice.payroll_service.processing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.payrollsevice.payroll_service.attendanceSummary.model.AttendanceSummary;
import com.payrollsevice.payroll_service.attendanceSummary.repository.AttendanceSummaryRepository;
import com.payrollsevice.payroll_service.payslip.dto.PayslipDtos;
import com.payrollsevice.payroll_service.payslip.model.Payslip;
import com.payrollsevice.payroll_service.processing.dto.PayrollDtos.CalculationRequest;
import com.payrollsevice.payroll_service.processing.model.Payroll;
import com.payrollsevice.payroll_service.processing.model.Payroll.Snapshot.Contract;
import com.payrollsevice.payroll_service.processing.repository.PayrollRepository;
import com.payrollsevice.payroll_service.rubric.model.Rubric;
import com.payrollsevice.payroll_service.rubric.model.RubricType;
import com.payrollsevice.payroll_service.rubric.repository.RubricRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegularPayrollCalculator {
    private final PayrollRepository payrollRepository;
    private final AttendanceSummaryRepository attendanceRepository;
    private final RubricRepository rubricRepository;

    @Transactional(readOnly = true)
    public List<PayslipDtos.Preview> preview(UUID payrollId, CalculationRequest request) {
        Payroll payroll = payrollRepository.findById(payrollId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll not found."));
        var contracts = payroll.getSnapshotPayload().contracts();
        if (contracts.isEmpty()) {
            return List.of();
        }

        var rubricIds = List.of(request.salaryRubricId(), request.overtimeRubricId(), request.absenceRubricId());
        if (rubricIds.stream().distinct().count() != rubricIds.size()) {
            throw invalid("Calculation requires three distinct rubrics.");
        }
        var rubrics = rubricRepository.findAllById(rubricIds).stream()
            .collect(Collectors.toMap(Rubric::getId, Function.identity()));
        Rubric salary = requireRubric(rubrics, request.salaryRubricId(), payroll.getCompanyId(), RubricType.EARNING);
        Rubric overtime = requireRubric(rubrics, request.overtimeRubricId(), payroll.getCompanyId(), RubricType.EARNING);
        Rubric absence = requireRubric(rubrics, request.absenceRubricId(), payroll.getCompanyId(), RubricType.DEDUCTION);
        var contractIds = contracts.stream().map(Contract::contractId).toList();
        var attendance = attendanceRepository.findByPeriodAndContractIdIn(payroll.getPeriod(), contractIds).stream()
            .collect(Collectors.toMap(AttendanceSummary::getContractId, Function.identity()));

        return contracts.stream().map(contract -> {
            AttendanceSummary summary = attendance.get(contract.contractId());
            if (summary == null) {
                throw invalid("Attendance summary is missing for contract " + contract.contractId() + ".");
            }
            return PayslipDtos.Preview.from(calculate(payroll, contract, summary, salary, overtime, absence,
                request.overtimeMultiplier()));
        }).toList();
    }

    private static Payslip calculate(Payroll payroll, Contract contract, AttendanceSummary attendance,
                                     Rubric salary, Rubric overtime, Rubric absence, BigDecimal multiplier) {
        long activeDays = ChronoUnit.DAYS.between(contract.periodStart(), contract.periodEnd()) + 1;
        BigDecimal salaryDays = contract.salaryPeriods().stream()
            .map(segment -> segment.salary().multiply(
                BigDecimal.valueOf(ChronoUnit.DAYS.between(segment.from(), segment.until()) + 1)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        int monthDays = YearMonth.from(payroll.getPeriod()).lengthOfMonth();
        BigDecimal base = divideMoney(salaryDays, monthDays);
        long averageDivisor = activeDays * contract.monthlyWorkHours() * 60L;
        if (averageDivisor <= 0) {
            throw invalid("Invalid working hours or dates for contract " + contract.contractId() + ".");
        }

        BigDecimal overtimePay = divideMoney(
            salaryDays.multiply(BigDecimal.valueOf(attendance.getOvertimeMinutes())).multiply(multiplier), averageDivisor);
        BigDecimal absenceDeduction = divideMoney(
            salaryDays.multiply(BigDecimal.valueOf(attendance.getAbsenceMinutes())), averageDivisor);

        Payslip payslip = new Payslip();
        payslip.setPayroll(payroll);
        payslip.setContractId(contract.contractId());
        payslip.setAppliedBaseSalary(base);
        payslip.getItems().add(new PayslipDtos.ItemRequest(salary.getId(), null, base).toItem(salary));
        if (attendance.getOvertimeMinutes() > 0) {
            payslip.getItems().add(new PayslipDtos.ItemRequest(overtime.getId(),
                BigDecimal.valueOf(attendance.getOvertimeMinutes()), overtimePay).toItem(overtime));
        }
        if (attendance.getAbsenceMinutes() > 0) {
            payslip.getItems().add(new PayslipDtos.ItemRequest(absence.getId(),
                BigDecimal.valueOf(attendance.getAbsenceMinutes()), absenceDeduction).toItem(absence));
        }

        BigDecimal earnings = base.add(overtimePay);
        if (earnings.precision() > 19 || absenceDeduction.precision() > 19) {
            throw invalid("Calculated totals exceed the supported range for contract " + contract.contractId() + ".");
        }
        payslip.setTotalEarnings(earnings);
        payslip.setTotalDeductions(absenceDeduction);
        payslip.setNetPay(earnings.subtract(absenceDeduction));
        return payslip;
    }

    private static Rubric requireRubric(Map<UUID, Rubric> rubrics, UUID id, UUID companyId, RubricType type) {
        Rubric rubric = rubrics.get(id);
        if (rubric == null || !companyId.equals(rubric.getCompanyId()) || rubric.getType() != type) {
            throw invalid("Rubric " + id + " must belong to the payroll company and have type " + type + ".");
        }
        return rubric;
    }

    private static BigDecimal divideMoney(BigDecimal amount, long divisor) {
        return amount.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message);
    }
}
