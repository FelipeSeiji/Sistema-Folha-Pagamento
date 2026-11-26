package com.payrollsevice.payroll_service.payslip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import com.payrollsevice.payroll_service.payslip.model.Payslip;
import com.payrollsevice.payroll_service.payslip.model.PayslipItem;
import com.payrollsevice.payroll_service.payslip.repository.PayslipRepository;
import com.payrollsevice.payroll_service.processing.model.Payroll;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.rubric.model.RubricType;

class PayslipPdfServiceTests {
    private final PayslipRepository repository = mock(PayslipRepository.class);
    private final PayslipPdfService service = new PayslipPdfService(repository);

    @Test
    void generatesReadablePdfWithSnapshotIdentityAndStoredAmounts(@TempDir Path output) throws Exception {
        Payslip payslip = example();
        var path = Files.write(output.resolve("holerite-ficticio.pdf"), service.generate(payslip.getId()));
        try (var document = Loader.loadPDF(path.toFile())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document))
                .contains("Competência 11/2026", "João da Conceição", "Matrícula: 0042",
                    "001 - Salário mensal", "002 - Desconto de ausência", "3.000,00", "100,00",
                    "Valor líquido", "2.900,00");
        }
    }

    @Test
    void wrapsLongDescriptionsAndPaginatesWithoutDroppingItems() throws Exception {
        Payslip payslip = example();
        for (int index = 1; index <= 35; index++) {
            payslip.getItems().add(item("EXTRA-" + index, "Descrição complementar ".repeat(9).strip(),
                RubricType.EARNING, "0.00"));
        }

        try (var document = Loader.loadPDF(service.generate(payslip.getId()))) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PDFTextStripper().getText(document)).contains("EXTRA-35", "Valor líquido", "2.900,00");
        }
    }

    @Test
    void refusesToGeneratePdfForDraftPayroll() {
        Payslip payslip = example();
        payslip.getPayroll().setStatus(PayrollStatus.DRAFT);
        assertThatThrownBy(() -> service.generate(payslip.getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode().value()).isEqualTo(409));
    }

    private Payslip example() {
        UUID contractId = UUID.randomUUID();
        LocalDate period = LocalDate.of(2026, 11, 1);
        Payroll payroll = new Payroll();
        payroll.setPeriod(period);
        payroll.setStatus(PayrollStatus.CALCULATED);
        payroll.setSnapshotPayload(new Payroll.Snapshot(List.of(new Payroll.Snapshot.Contract(
            contractId, UUID.randomUUID(), "João da Conceição", "0042", period, null, period,
            period.withDayOfMonth(30), 220, List.of()))));

        Payslip payslip = new Payslip();
        payslip.setId(UUID.randomUUID());
        payslip.setPayroll(payroll);
        payslip.setContractId(contractId);
        payslip.setAppliedBaseSalary(new BigDecimal("3000.00"));
        payslip.setTotalEarnings(new BigDecimal("3000.00"));
        payslip.setTotalDeductions(new BigDecimal("100.00"));
        payslip.setNetPay(new BigDecimal("2900.00"));
        payslip.getItems().add(item("001", "Salário mensal", RubricType.EARNING, "3000.00"));
        payslip.getItems().add(item("002", "Desconto de ausência", RubricType.DEDUCTION, "100.00"));
        when(repository.findById(payslip.getId())).thenReturn(Optional.of(payslip));
        return payslip;
    }

    private static PayslipItem item(String code, String description, RubricType type, String amount) {
        PayslipItem item = new PayslipItem();
        item.setAppliedCode(code);
        item.setAppliedDescription(description);
        item.setAppliedType(type);
        item.setAmount(new BigDecimal(amount));
        return item;
    }
}
