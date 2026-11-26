package com.payrollsevice.payroll_service.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

public final class PayrollCompanyAccess {
    private PayrollCompanyAccess() {
    }

    public static void require(Jwt principal, UUID companyId) {
        if (!companyIds(principal).contains(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access to this payroll company is required.");
        }
    }

    public static Set<UUID> companyIds(Jwt principal) {
        if (principal == null || !(principal.getClaims().get("company_ids") instanceof Collection<?> values)) {
            return Set.of();
        }

        Set<UUID> companies = new HashSet<>();
        for (Object value : values) {
            if (value instanceof String text) {
                try {
                    UUID id = UUID.fromString(text);
                    if (id.toString().equals(text)) {
                        companies.add(id);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid identifiers grant no company access.
                }
            }
        }
        return companies;
    }
}
