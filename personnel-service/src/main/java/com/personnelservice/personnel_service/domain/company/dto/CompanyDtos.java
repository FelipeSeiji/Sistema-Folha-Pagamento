package com.personnelservice.personnel_service.domain.company.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CompanyDtos {
    public record Request(@NotBlank String cnpj, @NotBlank String legalName) {
    }

    public record Response(UUID id, String cnpj, String legalName) {
    }
}
