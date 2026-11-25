package com.payrollsevice.payroll_service.outbox.model;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TB_PAYROLL_OUTBOX")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollOutboxEvent {
    public static final String PAYROLL_CLOSED = "PayrollClosed";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID payrollId;

    @Column(nullable = false, updatable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private PayrollClosed payload;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @Setter
    private Instant publishedAt;

    public PayrollOutboxEvent(UUID payrollId, UUID companyId, YearMonth period, Instant occurredAt) {
        this.payrollId = payrollId;
        this.eventType = PAYROLL_CLOSED;
        this.payload = new PayrollClosed(payrollId, companyId, period);
        this.occurredAt = occurredAt;
    }

    public record PayrollClosed(UUID payrollId, UUID companyId, YearMonth period) {
    }
}
