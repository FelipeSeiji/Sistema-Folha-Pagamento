package com.payrollsevice.payroll_service.payslip.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import com.payrollsevice.payroll_service.processing.model.Payroll;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TB_PAYSLIP")
@Getter
@Setter
@NoArgsConstructor
public class Payslip {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_id", nullable = false)
    private Payroll payroll;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal appliedBaseSalary;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalEarnings;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDeductions;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netPay;

    @ElementCollection
    @CollectionTable(name = "TB_PAYSLIP_ITEM", joinColumns = @JoinColumn(name = "payslip_id", nullable = false))
    @OrderColumn(name = "item_order")
    private List<PayslipItem> items = new ArrayList<>();
}
