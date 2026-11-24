package com.personnelservice.personnel_service.domain.department.dto;

import java.util.UUID;

import com.personnelservice.personnel_service.domain.department.model.Department;

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

    public record UpdateRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record Response(UUID id, String name, UUID companyId, boolean active) {
        public static Response from(Department department) {
            return new Response(department.getId(), department.getName(), department.getCompany().getId(), department.isActive());
        }
    }
}
