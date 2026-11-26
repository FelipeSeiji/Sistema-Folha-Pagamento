package com.payrollsevice.payroll_service.attendanceSummary.model;

import java.util.UUID;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TB_ATTENDANCE_SUMMARY")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceSummary {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    private UUID companyId;

    @Column(nullable = false)
    private LocalDate period;

    @Column(nullable = false)
    private long workedMinutes;

    @Column(nullable = false)
    private long overtimeMinutes;

    @Column(nullable = false)
    private long absenceMinutes;
}
