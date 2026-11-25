package com.payrollsevice.payroll_service.outbox.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.payrollsevice.payroll_service.outbox.model.PayrollOutboxEvent;

import jakarta.persistence.LockModeType;

public interface PayrollOutboxRepository extends JpaRepository<PayrollOutboxEvent, UUID> {
    List<PayrollOutboxEvent> findByPayrollId(UUID payrollId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PayrollOutboxEvent> findFirstByPublishedAtIsNullOrderByOccurredAtAscIdAsc();
}
