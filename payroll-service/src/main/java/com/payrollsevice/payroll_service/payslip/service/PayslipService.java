package com.payrollsevice.payroll_service.payslip.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.payrollsevice.payroll_service.payslip.dto.PayslipDtos;
import com.payrollsevice.payroll_service.payslip.model.Payslip;
import com.payrollsevice.payroll_service.payslip.repository.PayslipRepository;
import com.payrollsevice.payroll_service.processing.model.Payroll;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.processing.repository.PayrollRepository;
import com.payrollsevice.payroll_service.rubric.model.Rubric;
import com.payrollsevice.payroll_service.rubric.model.RubricType;
import com.payrollsevice.payroll_service.rubric.repository.RubricRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayslipService {
    private final PayslipRepository repository;
    private final PayrollRepository payrollRepository;
    private final RubricRepository rubricRepository;

    @Transactional
    public PayslipDtos.Response create(PayslipDtos.Request request) {
        var payroll = payrollRepository.findById(request.payrollId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll not found."));
        requireDraft(payroll);
        if (payroll.getSnapshotPayload().contracts().stream()
                .noneMatch(contract -> contract.contractId().equals(request.contractId()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                "Contract is absent from the payroll snapshot.");
        }

        Payslip entity = new Payslip();
        entity.setPayroll(payroll);
        entity.setContractId(request.contractId());
        applyChanges(entity, request);
        return PayslipDtos.Response.from(repository.saveAndFlush(entity));
    }

    @Transactional
    public PayslipDtos.Response update(UUID id, PayslipDtos.Request request) {
        Payslip entity = requirePayslipForUpdate(id);
        if (!entity.getPayroll().getId().equals(request.payrollId())
                || !entity.getContractId().equals(request.contractId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payslip payroll and contract cannot be changed.");
        }
        requireDraft(entity.getPayroll());
        applyChanges(entity, request);
        repository.flush();
        return PayslipDtos.Response.from(entity);
    }

    private void applyChanges(Payslip entity, PayslipDtos.Request request) {
        var rubricIds = request.items().stream().map(PayslipDtos.ItemRequest::rubricId).distinct().toList();
        Map<UUID, Rubric> rubrics = new HashMap<>();
        rubricRepository.findAllById(rubricIds).forEach(rubric -> rubrics.put(rubric.getId(), rubric));

        entity.getItems().clear();
        entity.setAppliedBaseSalary(request.appliedBaseSalary());

        BigDecimal earnings = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;
        for (var requestedItem : request.items()) {
            Rubric rubric = rubrics.get(requestedItem.rubricId());
            if (rubric == null || !entity.getPayroll().getCompanyId().equals(rubric.getCompanyId())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Every item must reference a rubric from the payroll company.");
            }

            entity.getItems().add(requestedItem.toItem(rubric));
            if (rubric.getType() == RubricType.EARNING) {
                earnings = earnings.add(requestedItem.amount());
            } else {
                deductions = deductions.add(requestedItem.amount());
            }
        }
        if (earnings.setScale(2).precision() > 19 || deductions.setScale(2).precision() > 19) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Payslip totals exceed the supported range.");
        }
        entity.setTotalEarnings(earnings);
        entity.setTotalDeductions(deductions);
        entity.setNetPay(earnings.subtract(deductions));
    }

    private static void requireDraft(Payroll payroll) {
        if (payroll.getStatus() != PayrollStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft payrolls allow payslip changes.");
        }
    }

    @Transactional
    public void delete(UUID id) {
        Payslip payslip = requirePayslipForUpdate(id);
        requireDraft(payslip.getPayroll());
        repository.delete(payslip);
    }

    @Transactional(readOnly = true)
    public Page<PayslipDtos.Response> list(Pageable pageable) {
        return repository.findAll(pageable).map(PayslipDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public PayslipDtos.Response findById(UUID id) {
        return repository.findById(id)
            .map(PayslipDtos.Response::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payslip not found."));
    }

    private Payslip requirePayslipForUpdate(UUID id) {
        return repository.findForUpdateById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payslip not found."));
    }
}
