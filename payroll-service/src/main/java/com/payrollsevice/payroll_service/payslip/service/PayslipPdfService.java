package com.payrollsevice.payroll_service.payslip.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.payrollsevice.payroll_service.payslip.model.Payslip;
import com.payrollsevice.payroll_service.payslip.repository.PayslipRepository;
import com.payrollsevice.payroll_service.processing.model.Payroll.Snapshot.Contract;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.rubric.model.RubricType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayslipPdfService {
    private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("MM/yyyy", BRAZIL);
    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    private static final int LEFT = 42;
    private static final int RIGHT = 553;
    private static final int EARNINGS_RIGHT = 432;
    private static final int LINE_HEIGHT = 14;

    private final PayslipRepository repository;

    @Transactional(readOnly = true)
    public byte[] generate(UUID payslipId) {
        Payslip payslip = repository.findById(payslipId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payslip not found."));
        var payroll = payslip.getPayroll();
        if (payroll.getStatus() == PayrollStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payroll must be calculated to generate a payslip PDF.");
        }

        Contract contract = payroll.getSnapshotPayload().contracts().stream()
            .filter(value -> payslip.getContractId().equals(value.contractId()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Payslip contract is missing from the payroll snapshot."));

        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Holerite - " + YearMonth.from(payroll.getPeriod()));
            render(document, payslip, contract);
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not generate payslip PDF.", exception);
        }
    }

    private static void render(PDDocument document, Payslip payslip, Contract contract) throws IOException {
        var regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        var money = NumberFormat.getCurrencyInstance(BRAZIL);
        var rows = new ArrayList<Row>();
        for (var item : payslip.getItems()) {
            String label = item.getAppliedCode() + " - " + item.getAppliedDescription();
            rows.add(new Row(wrap(label, regular, 9, 263),
                item.getAppliedType(), item.getAmount()));
        }

        int index = 0;
        boolean finished = false;
        while (!finished) {
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                int y = header(stream, payslip, contract, regular, bold);
                while (index < rows.size()) {
                    Row row = rows.get(index);
                    int height = row.lines().size() * LINE_HEIGHT + 6;
                    if (y - height < 60) {
                        break;
                    }
                    int labelY = y;
                    for (String line : row.lines()) {
                        text(stream, regular, 9, LEFT, labelY, line);
                        labelY -= LINE_HEIGHT;
                    }
                    int right = row.type() == RubricType.EARNING ? EARNINGS_RIGHT : RIGHT;
                    rightText(stream, regular, 8, right, y, money.format(row.amount()));
                    y -= height;
                    rule(stream, y + 7);
                    index++;
                }

                if (index == rows.size() && y >= 128) {
                    totals(stream, payslip, money, bold, y - 9);
                    finished = true;
                }
                rightText(stream, regular, 8, RIGHT, 36, "Página " + document.getNumberOfPages());
            }
        }
    }

    private static int header(PDPageContentStream stream, Payslip payslip, Contract contract,
                              PDType1Font regular, PDType1Font bold) throws IOException {
        text(stream, bold, 23, LEFT, 788, "Holerite");
        rightText(stream, bold, 11, RIGHT, 791, "Competência " + PERIOD_FORMAT.format(payslip.getPayroll().getPeriod()));
        rule(stream, 772);

        int y = 750;
        for (String line : wrap("Colaborador: " + contract.employeeName(), regular, 10, RIGHT - LEFT)) {
            text(stream, regular, 10, LEFT, y, line);
            y -= LINE_HEIGHT;
        }
        for (String line : wrap("Matrícula: " + contract.registrationNumber(), regular, 10, RIGHT - LEFT)) {
            text(stream, regular, 10, LEFT, y, line);
            y -= LINE_HEIGHT;
        }

        y -= 15;
        text(stream, bold, 10, LEFT, y, "Rubrica");
        rightText(stream, bold, 10, EARNINGS_RIGHT, y, "Proventos");
        rightText(stream, bold, 10, RIGHT, y, "Descontos");
        rule(stream, y - 8);
        return y - 24;
    }

    private static void totals(PDPageContentStream stream, Payslip payslip, NumberFormat money,
                               PDType1Font bold, int y) throws IOException {
        text(stream, bold, 10, LEFT, y, "Salário-base aplicado");
        rightText(stream, bold, 10, RIGHT, y, money.format(payslip.getAppliedBaseSalary()));
        text(stream, bold, 10, LEFT, y - 18, "Total de proventos");
        rightText(stream, bold, 10, RIGHT, y - 18, money.format(payslip.getTotalEarnings()));
        text(stream, bold, 10, LEFT, y - 36, "Total de descontos");
        rightText(stream, bold, 10, RIGHT, y - 36, money.format(payslip.getTotalDeductions()));
        rule(stream, y - 45);
        text(stream, bold, 12, LEFT, y - 62, "Valor líquido");
        rightText(stream, bold, 12, RIGHT, y - 62, money.format(payslip.getNetPay()));
    }

    private static List<String> wrap(String value, PDType1Font font, int size, int maxWidth)
            throws IOException {
        var lines = new ArrayList<String>();
        String line = "";
        for (String word : printable(value).split(" +")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(font, size, candidate) <= maxWidth) {
                line = candidate;
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line);
            }
            line = "";
            for (int index = 0; index < word.length(); index++) {
                char character = word.charAt(index);
                if (width(font, size, line + character) > maxWidth) {
                    lines.add(line);
                    line = "";
                }
                line += character;
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    private static String printable(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
            .replaceAll("[\\p{Cc}\\p{Z}]", " ");
        return new String(normalized.getBytes(WIN_ANSI), WIN_ANSI);
    }

    private static float width(PDType1Font font, int size, String value) throws IOException {
        return font.getStringWidth(value) * size / 1000;
    }

    private static void rightText(PDPageContentStream stream, PDType1Font font, int size,
                                  int right, int y, String value) throws IOException {
        String safe = printable(value);
        text(stream, font, size, right - width(font, size, safe), y, safe);
    }

    private static void text(PDPageContentStream stream, PDType1Font font, int size,
                             float x, int y, String value) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(value);
        stream.endText();
    }

    private static void rule(PDPageContentStream stream, int y) throws IOException {
        stream.setStrokingColor(0.76f);
        stream.moveTo(LEFT, y);
        stream.lineTo(RIGHT, y);
        stream.stroke();
    }

    private record Row(List<String> lines, RubricType type, BigDecimal amount) {
    }
}
