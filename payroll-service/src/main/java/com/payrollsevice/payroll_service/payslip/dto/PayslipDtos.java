package com.payrollsevice.payroll_service.payslip.dto;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import com.payrollsevice.payroll_service.rubric.model.RubricType;
import com.payrollsevice.payroll_service.payslip.model.Payslip;
import com.payrollsevice.payroll_service.payslip.model.PayslipItem;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PayslipDtos {
    public record Response(
        UUID id, UUID payrollId, UUID contractId, BigDecimal appliedBaseSalary,
        BigDecimal totalEarnings, BigDecimal totalDeductions, BigDecimal netPay, List<ItemResponse> items
    ) {
        public static Response from(Payslip entity) {
            return new Response(entity.getId(), entity.getPayroll().getId(), entity.getContractId(),
                entity.getAppliedBaseSalary(), entity.getTotalEarnings(), entity.getTotalDeductions(), entity.getNetPay(),
                entity.getItems().stream().map(ItemResponse::from).toList());
        }
    }

    public record ItemResponse(
        UUID rubricId, String appliedCode, String appliedDescription,
        RubricType appliedType, BigDecimal reference, BigDecimal amount
    ) {
        static ItemResponse from(PayslipItem item) {
            return new ItemResponse(item.getRubric().getId(), item.getAppliedCode(), item.getAppliedDescription(),
                item.getAppliedType(), item.getReference(), item.getAmount());
        }
    }
}
