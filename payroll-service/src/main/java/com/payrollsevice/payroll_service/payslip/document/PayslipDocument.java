package com.payrollsevice.payroll_service.payslip.document;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "TB_PAYSLIP_DOCUMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PayslipDocument {
    @Id
    private UUID payslipId;

    @Column(nullable = false)
    private byte[] pdf;
}
