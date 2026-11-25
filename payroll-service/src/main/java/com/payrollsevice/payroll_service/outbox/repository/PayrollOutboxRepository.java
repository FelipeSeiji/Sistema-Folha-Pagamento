package com.payrollsevice.payroll_service.outbox.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payrollsevice.payroll_service.outbox.model.PayrollOutboxEvent;

public interface PayrollOutboxRepository extends JpaRepository<PayrollOutboxEvent, UUID> {
    List<PayrollOutboxEvent> findByPayrollId(UUID payrollId);
}
