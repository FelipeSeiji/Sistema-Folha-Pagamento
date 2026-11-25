package com.payrollsevice.payroll_service.personnel;

public class PersonnelUnavailableException extends RuntimeException {
    public PersonnelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
