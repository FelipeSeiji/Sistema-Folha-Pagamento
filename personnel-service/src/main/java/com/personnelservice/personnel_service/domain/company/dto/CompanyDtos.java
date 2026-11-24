package com.personnelservice.personnel_service.domain.company.dto;

import java.util.Locale;
import java.util.UUID;

import org.hibernate.validator.constraints.CodePointLength;

import com.personnelservice.personnel_service.domain.company.model.Company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CompanyDtos {
    public record Request(
        @NotBlank @Pattern(regexp = "[A-Z0-9]{12}[0-9]{2}") String cnpj,
        @NotBlank @CodePointLength(max = 255) String legalName
    ) {
        public Request {
            legalName = legalName == null ? null : legalName.strip();
            if (cnpj != null) {
                cnpj = cnpj.strip()
                    .replace(".", "")
                    .replace("/", "")
                    .replace("-", "")
                    .toUpperCase(Locale.ROOT);
            }
        }
    }

    public record UpdateRequest(@NotBlank @CodePointLength(max = 255) String legalName) {
        public UpdateRequest {
            legalName = legalName == null ? null : legalName.strip();
        }
    }

    public record Response(UUID id, String cnpj, String legalName) {
        public static Response from(Company company) {
            return new Response(company.getId(), company.getCnpj(), company.getLegalName());
        }
    }
}
