package com.payrollsevice.payroll_service.processing.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

import com.payrollsevice.payroll_service.processing.dto.PayrollDtos.FinanceReport;

final class FinanceReportCsv {
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("MM/yyyy");

    private FinanceReportCsv() {
    }

    static byte[] encode(FinanceReport report) {
        var csv = new StringBuilder("\uFEFFCompetência;Nome;Matrícula;Valor líquido\r\n");
        String period = report.period().format(PERIOD_FORMAT);

        for (var entry : report.entries()) {
            csv.append(period).append(';')
                .append(text(entry.employeeName())).append(';')
                .append(text(entry.registrationNumber())).append(';')
                .append(amount(entry.netPay())).append("\r\n");
        }

        csv.append(period).append(";TOTAL;;").append(amount(report.totalNetPay())).append("\r\n");
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String amount(BigDecimal value) {
        return value.setScale(2).toPlainString().replace('.', ',');
    }

    private static String text(String value) {
        String stripped = value.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
