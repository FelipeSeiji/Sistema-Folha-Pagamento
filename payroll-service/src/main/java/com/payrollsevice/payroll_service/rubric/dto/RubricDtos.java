package com.payrollsevice.payroll_service.rubric.dto;

import java.util.UUID;

import com.payrollsevice.payroll_service.rubric.model.Rubric;
import com.payrollsevice.payroll_service.rubric.model.RubricType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RubricDtos {
    public record Request(
        @NotNull UUID companyId,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String description,
        @NotNull RubricType type,
        boolean subjectToInss, boolean subjectToIrrf, boolean subjectToFgts
    ) {
    }

    public record Response(
        UUID id, UUID companyId, String code, String description, RubricType type,
        boolean subjectToInss, boolean subjectToIrrf, boolean subjectToFgts
    ) {
        public static Response from(Rubric entity) {
            return new Response(entity.getId(), entity.getCompanyId(), entity.getCode(), entity.getDescription(),
                entity.getType(), entity.isSubjectToInss(), entity.isSubjectToIrrf(), entity.isSubjectToFgts());
        }
    }
}
