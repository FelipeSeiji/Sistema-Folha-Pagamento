package com.payrollsevice.payroll_service.payslip.dto;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import com.payrollsevice.payroll_service.rubric.model.Rubric;
import com.payrollsevice.payroll_service.rubric.model.RubricType;
import com.payrollsevice.payroll_service.payslip.model.Payslip;
import com.payrollsevice.payroll_service.payslip.model.PayslipItem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PayslipDtos {
    public record Request(
        @NotNull UUID payrollId,
        @NotNull UUID contractId,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal appliedBaseSalary,
        @NotEmpty List<@NotNull @Valid ItemRequest> items
    ) {
    }

    public record ItemRequest(
        @NotNull UUID rubricId,
        @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal reference,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal amount
    ) {
        public PayslipItem toItem(Rubric rubric) {
            PayslipItem item = new PayslipItem();
            item.setRubric(rubric);
            item.setAppliedCode(rubric.getCode());
            item.setAppliedDescription(rubric.getDescription());
            item.setAppliedType(rubric.getType());
            item.setReference(reference);
            item.setAmount(amount);
            return item;
        }
    }

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
