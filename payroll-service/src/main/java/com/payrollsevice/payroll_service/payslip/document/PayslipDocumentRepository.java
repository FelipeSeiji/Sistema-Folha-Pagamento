package com.payrollsevice.payroll_service.payslip.document;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipDocumentRepository extends JpaRepository<PayslipDocument, UUID> {
}
