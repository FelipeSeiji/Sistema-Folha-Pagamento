package com.payrollsevice.payroll_service.error;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DataIntegrityHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handle(DataIntegrityViolationException exception) {
        if (exception.getCause() instanceof ConstraintViolationException violation) {
            if ("23505".equals(violation.getSQLState())) {
                return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A record with this key already exists.");
            }
            if ("23503".equals(violation.getSQLState())) {
                return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The operation conflicts with a related record.");
            }
        }
        throw exception;
    }
}
