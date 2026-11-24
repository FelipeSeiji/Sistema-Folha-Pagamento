package com.personnelservice.personnel_service.domain.department.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DepartmentDtos {
    public record Request(
        @NotBlank @Size(max = 100) String name,
        @NotNull UUID companyId
    ) {
    }

    public record Response(UUID id, String name, UUID companyId) {
    }
}
