package com.payrollsevice.payroll_service.personnel;

import java.time.YearMonth;
import java.util.UUID;

public class PersonnelInputConflictException extends RuntimeException {
    public PersonnelInputConflictException(UUID companyId, YearMonth period) {
        super("Personnel reported inconsistent salary history for company " + companyId + " in " + period + ".");
    }
}
