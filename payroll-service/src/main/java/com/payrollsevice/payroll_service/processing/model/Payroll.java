package com.payrollsevice.payroll_service.processing.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TB_PAYROLL")
@Getter
@Setter
@NoArgsConstructor
public class Payroll {
    @Id
    @GeneratedValue
    private UUID id;

    @Version
    private Long version;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private LocalDate period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PayrollType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PayrollStatus status = PayrollStatus.DRAFT;

    @Column(name = "snapshot_captured_at", nullable = false, updatable = false)
    private Instant snapshotCapturedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_payload", nullable = false, updatable = false)
    private Snapshot snapshotPayload;

    public record Snapshot(List<Contract> contracts) {
        public Snapshot {
            contracts = List.copyOf(contracts);
        }

        public record Contract(
            UUID contractId, UUID employeeId, String employeeName, String registrationNumber,
            LocalDate hireDate, LocalDate terminationDate, LocalDate periodStart, LocalDate periodEnd,
            int monthlyWorkHours, List<SalaryPeriod> salaryPeriods
        ) {
            public Contract {
                salaryPeriods = List.copyOf(salaryPeriods);
            }
        }

        public record SalaryPeriod(LocalDate from, LocalDate until, BigDecimal salary) {
        }
    }
}
