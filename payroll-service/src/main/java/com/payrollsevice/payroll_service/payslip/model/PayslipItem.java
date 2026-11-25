package com.payrollsevice.payroll_service.payslip.model;

import java.math.BigDecimal;

import com.payrollsevice.payroll_service.rubric.model.Rubric;
import com.payrollsevice.payroll_service.rubric.model.RubricType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class PayslipItem {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rubric_id", nullable = false)
    private Rubric rubric;

    @Column(nullable = false, length = 50)
    private String appliedCode;

    @Column(nullable = false, length = 255)
    private String appliedDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RubricType appliedType;

    @Column(precision = 19, scale = 4)
    private BigDecimal reference;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
}
