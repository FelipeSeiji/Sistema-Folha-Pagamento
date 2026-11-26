package com.payrollsevice.payroll_service.processing.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.payrollsevice.payroll_service.processing.dto.PayrollDtos.FinanceReport;
import com.payrollsevice.payroll_service.processing.dto.PayrollDtos.FinanceReportEntry;

class FinanceReportCsvTests {
    @Test
    void encodesAccentsQuotedTextLeadingZerosAndDecimalAmounts() {
        var report = new FinanceReport(YearMonth.of(2026, 11), List.of(
            new FinanceReportEntry("João; \"Júnior\"", "0042", new BigDecimal("2985.00")),
            new FinanceReportEntry(" =2+2", "0003", new BigDecimal("3000"))), new BigDecimal("5985"));

        assertThat(new String(FinanceReportCsv.encode(report), StandardCharsets.UTF_8)).isEqualTo(
            "\uFEFFCompetência;Nome;Matrícula;Valor líquido\r\n"
                + "11/2026;\"João; \"\"Júnior\"\"\";\"0042\";2985,00\r\n"
                + "11/2026;\"' =2+2\";\"0003\";3000,00\r\n"
                + "11/2026;TOTAL;;5985,00\r\n");
    }
}
