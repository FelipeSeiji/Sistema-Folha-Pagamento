package com.personnelservice.personnel_service.domain.salaryHistory.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.personnelservice.personnel_service.domain.employmentContract.model.EmploymentContract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TB_SALARY_HISTORY")
@Getter
@Setter
@NoArgsConstructor
public class SalaryHistory {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employment_contract_id")
    private EmploymentContract contract;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveUntil;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal salary;
}
