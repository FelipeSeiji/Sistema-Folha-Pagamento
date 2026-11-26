package com.payrollsevice.payroll_service.security;

import java.util.Collection;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

public final class PayrollCompanyAccess {
    private PayrollCompanyAccess() {
    }

    public static void require(Jwt principal, UUID companyId) {
        if (principal == null || !(principal.getClaims().get("company_ids") instanceof Collection<?> companyIds)
                || !companyIds.contains(companyId.toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access to this payroll company is required.");
        }
    }
}
