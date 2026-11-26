package com.payrollsevice.payroll_service.processing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.payrollsevice.payroll_service.attendanceSummary.dto.AttendanceSummaryDtos;
import com.payrollsevice.payroll_service.outbox.model.PayrollClosedMessage;
import com.payrollsevice.payroll_service.outbox.model.PayrollOutboxEvent;
import com.payrollsevice.payroll_service.outbox.repository.PayrollOutboxRepository;
import com.payrollsevice.payroll_service.payslip.document.PayslipDocumentRepository;
import com.payrollsevice.payroll_service.payslip.document.PayslipDocumentWorker;
import com.payrollsevice.payroll_service.payslip.dto.PayslipDtos;
import com.payrollsevice.payroll_service.payslip.model.Payslip;
import com.payrollsevice.payroll_service.payslip.repository.PayslipRepository;
import com.payrollsevice.payroll_service.personnel.PersonnelClient;
import com.payrollsevice.payroll_service.personnel.PersonnelUnavailableException;
import com.payrollsevice.payroll_service.personnel.dto.PersonnelPayrollInput;
import com.payrollsevice.payroll_service.processing.dto.PayrollDtos;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.processing.model.PayrollType;
import com.payrollsevice.payroll_service.processing.repository.ClosedPayrollSummaryRepository;
import com.payrollsevice.payroll_service.processing.repository.PayrollRepository;
import com.payrollsevice.payroll_service.processing.service.PayrollClosedConsumer;
import tools.jackson.databind.json.JsonMapper;
import com.payrollsevice.payroll_service.rubric.dto.RubricDtos;
import com.payrollsevice.payroll_service.rubric.model.RubricType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PayrollControllerTests {
    @LocalServerPort
    private int port;

    @MockitoBean
    private PersonnelClient personnelClient;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private PayrollRepository repository;

    @Autowired
    private PayslipRepository payslipRepository;

    @Autowired
    private PayrollOutboxRepository outboxRepository;

    @Autowired
    private PayrollClosedConsumer closedConsumer;

    @Autowired
    private ClosedPayrollSummaryRepository summaryRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PayslipDocumentWorker documentWorker;

    @Autowired
    private PayslipDocumentRepository documents;

    @Test
    void createsAndReadsDraftWithPersistedSnapshot() {
        UUID companyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        YearMonth period = YearMonth.of(2026, 11);
        var salary = new PersonnelPayrollInput.SalaryPeriod(period.atDay(1), period.atEndOfMonth(),
            new BigDecimal("3500.00"));
        var contract = new PersonnelPayrollInput.Contract(contractId, UUID.randomUUID(), "Jane Doe", "001",
            period.atDay(1), null, period.atDay(1), period.atEndOfMonth(), 220, List.of(salary));
        when(personnelClient.getPayrollInputs(companyId, period))
            .thenReturn(new PersonnelPayrollInput(companyId, period, List.of(contract)));

        RestClient client = client();
        var request = new PayrollDtos.CreateRequest(companyId, period, PayrollType.REGULAR);
        var created = client.post().uri("/payroll").body(request).retrieve().toEntity(PayrollDtos.Response.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        var body = created.getBody();
        assertThat(body.status()).isEqualTo(PayrollStatus.DRAFT);
        assertThat(created.getHeaders().getLocation().getPath()).isEqualTo("/payroll/" + body.id());

        assertThat(repository.findById(body.id()).orElseThrow().getSnapshotPayload().contracts())
            .singleElement().satisfies(saved -> {
                assertThat(saved.contractId()).isEqualTo(contractId);
                assertThat(saved.salaryPeriods().getFirst().salary()).isEqualByComparingTo(salary.salary());
            });
        assertThat(client.post().uri("/payroll").body(request)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);

        assertThat(client.get().uri("/payroll/{id}", body.id()).retrieve().body(PayrollDtos.Response.class))
            .isEqualTo(body);
        assertThat(client.get().uri("/payroll").retrieve().body(String.class)).contains(body.id().toString());
    }

    @Test
    void doesNotCreatePayrollWhenPersonnelIsUnavailable() {
        UUID companyId = UUID.randomUUID();
        YearMonth period = YearMonth.of(2026, 11);
        when(personnelClient.getPayrollInputs(companyId, period))
            .thenThrow(new PersonnelUnavailableException("Personnel is unavailable.", null));

        var request = new PayrollDtos.CreateRequest(companyId, period, PayrollType.REGULAR);
        assertThat(client().post().uri("/payroll").body(request)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(503);
        assertThat(repository.existsByCompanyIdAndPeriodAndType(companyId, period.atDay(1), PayrollType.REGULAR))
            .isFalse();
    }

    @Test
    void rejectsInvalidCreateRequest() {
        assertThat(client().post().uri("/payroll").contentType(MediaType.APPLICATION_JSON).body("{}")
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(400);
        verifyNoInteractions(personnelClient);
    }

    @Test
    void returnsNotFoundForUnknownPayroll() {
        assertThat(client().get().uri("/payroll/{id}", UUID.randomUUID())
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(404);
    }

    @Test
    void previewsProportionalSalaryOvertimeAndAbsence() {
        UUID companyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 11);
        var start = month.atDay(16);
        var end = month.atEndOfMonth();
        var salaries = List.of(
            new PersonnelPayrollInput.SalaryPeriod(start, month.atDay(20), new BigDecimal("3000")),
            new PersonnelPayrollInput.SalaryPeriod(month.atDay(21), end, new BigDecimal("3600")));
        var contract = new PersonnelPayrollInput.Contract(contractId, UUID.randomUUID(), "Jane Doe", "001",
            start, null, start, end, 200, salaries);
        when(personnelClient.getPayrollInputs(companyId, month))
            .thenReturn(new PersonnelPayrollInput(companyId, month, List.of(contract)));

        RestClient http = client();
        var payroll = http.post().uri("/payroll")
            .body(new PayrollDtos.CreateRequest(companyId, month, PayrollType.REGULAR))
            .retrieve().body(PayrollDtos.Response.class);
        UUID salary = createRubric(http, companyId, "SALARY", RubricType.EARNING);
        UUID overtime = createRubric(http, companyId, "OVERTIME", RubricType.EARNING);
        UUID absence = createRubric(http, companyId, "ABSENCE", RubricType.DEDUCTION);
        var request = new PayrollDtos.CalculationRequest(salary, overtime, absence, new BigDecimal("1.50"));
        String route = "/payroll/{id}/calculation-preview";

        assertThat(http.post().uri(route, payroll.id()).body(request)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(422);
        http.post().uri("/attendance-summary")
            .body(new AttendanceSummaryDtos.Request(contractId, month, 6000L, 120L, 60L))
            .retrieve().toBodilessEntity();

        var preview = http.post().uri(route, payroll.id()).body(request)
            .retrieve().body(PayslipDtos.Preview[].class);
        assertThat(preview).singleElement().satisfies(payslip -> {
            assertThat(payslip.contractId()).isEqualTo(contractId);
            assertThat(payslip.appliedBaseSalary()).isEqualByComparingTo("1700.00");
            assertThat(payslip.totalEarnings()).isEqualByComparingTo("1751.00");
            assertThat(payslip.totalDeductions()).isEqualByComparingTo("17.00");
            assertThat(payslip.netPay()).isEqualByComparingTo("1734.00");
            assertThat(payslip.items()).extracting(PayslipDtos.ItemResponse::rubricId)
                .containsExactly(salary, overtime, absence);
        });
        assertThat(repository.findById(payroll.id()).orElseThrow().getStatus()).isEqualTo(PayrollStatus.DRAFT);
        var calculated = http.post().uri("/payroll/{id}/calculate", payroll.id()).body(request)
            .retrieve().body(PayslipDtos.Response[].class);
        assertThat(calculated).singleElement().satisfies(payslip -> {
            assertThat(payslip.id()).isNotNull();
            assertThat(payslip.contractId()).isEqualTo(contractId);
            assertThat(payslip.netPay()).isEqualByComparingTo(preview[0].netPay());
            var persisted = http.get().uri("/payslip/{id}", payslip.id())
                .retrieve().body(PayslipDtos.Response.class);
            assertThat(persisted.netPay()).isEqualByComparingTo(payslip.netPay());
            assertThat(persisted.items()).extracting(PayslipDtos.ItemResponse::rubricId)
                .containsExactly(salary, overtime, absence);
        });
        assertThat(repository.findById(payroll.id()).orElseThrow().getStatus()).isEqualTo(PayrollStatus.CALCULATED);
        assertThat(http.post().uri("/payroll/{id}/calculate", payroll.id()).body(request)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);
    }

    @Test
    void rollsBackAllChangesWhenTheSecondPayslipConflicts() {
        var fixture = prepareCalculation();
        var request = new PayslipDtos.Request(fixture.payrollId(), fixture.secondContractId(),
            new BigDecimal("2500.00"), List.of(new PayslipDtos.ItemRequest(
                fixture.request().salaryRubricId(), null, new BigDecimal("2500.00"))));
        var manual = client().post().uri("/payslip").body(request)
            .retrieve().body(PayslipDtos.Response.class);

        assertThat(calculateStatus(fixture)).isEqualTo(409);
        assertThat(repository.findById(fixture.payrollId()).orElseThrow().getStatus())
            .isEqualTo(PayrollStatus.DRAFT);
        assertThat(payslipRepository.findByPayrollId(fixture.payrollId())).singleElement().satisfies(payslip -> {
            assertThat(payslip.getId()).isEqualTo(manual.id());
            assertThat(payslip.getContractId()).isEqualTo(fixture.secondContractId());
            assertThat(payslip.getNetPay()).isEqualByComparingTo("2500.00");
        });
    }

    @Test
    void concurrentCalculationRequestsCreateOnlyOnePayslipPerContract() throws Exception {
        var fixture = prepareCalculation();
        assertThat(runConcurrently(() -> calculateStatus(fixture))).containsExactlyInAnyOrder(200, 409);

        assertThat(repository.findById(fixture.payrollId()).orElseThrow().getStatus())
            .isEqualTo(PayrollStatus.CALCULATED);
        assertThat(payslipRepository.findByPayrollId(fixture.payrollId())).extracting(Payslip::getContractId)
            .containsExactlyInAnyOrder(fixture.firstContractId(), fixture.secondContractId());
    }

    @Test
    void closesOnlyCalculatedPayrolls() {
        var fixture = prepareCalculation();
        RestClient http = client();
        String route = "/payroll/{id}/close";
        UUID payrollId = fixture.payrollId();

        assertThat(http.post().uri(route, UUID.randomUUID())
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(404);
        assertThat(http.post().uri(route, payrollId)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);
        assertThat(calculateStatus(fixture)).isEqualTo(200);

        var closed = http.post().uri(route, payrollId).retrieve().toEntity(PayrollDtos.Response.class);
        assertThat(closed.getStatusCode().value()).isEqualTo(200);
        assertThat(closed.getBody().status()).isEqualTo(PayrollStatus.CLOSED);
        assertThat(repository.findById(payrollId).orElseThrow().getStatus()).isEqualTo(PayrollStatus.CLOSED);
        assertThat(http.post().uri(route, payrollId)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);
    }

    @Test
    void concurrentClosingRequestsCloseThePayrollExactlyOnce() throws Exception {
        var fixture = prepareCalculation();
        assertThat(calculateStatus(fixture)).isEqualTo(200);
        UUID payrollId = fixture.payrollId();
        long version = repository.findById(payrollId).orElseThrow().getVersion();

        var statuses = runConcurrently(() -> client().post().uri("/payroll/{id}/close", payrollId)
            .exchange((sent, response) -> response.getStatusCode().value()));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        var closed = repository.findById(payrollId).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(PayrollStatus.CLOSED);
        assertThat(closed.getVersion()).isEqualTo(version + 1);
        assertThat(outboxRepository.findByPayrollId(payrollId)).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(PayrollOutboxEvent.PAYROLL_CLOSED);
            assertThat(event.getPayload()).isEqualTo(new PayrollOutboxEvent.PayrollClosed(
                payrollId, closed.getCompanyId(), YearMonth.from(closed.getPeriod())));
            assertThat(event.getOccurredAt()).isNotNull();
            assertThat(event.getPublishedAt()).isNull();
        });
    }

    @Test
    void rollsBackClosingWhenTheOutboxInsertConflicts() {
        var fixture = prepareCalculation();
        assertThat(calculateStatus(fixture)).isEqualTo(200);
        var calculated = repository.findById(fixture.payrollId()).orElseThrow();
        var existing = outboxRepository.saveAndFlush(new PayrollOutboxEvent(calculated.getId(),
            calculated.getCompanyId(), YearMonth.from(calculated.getPeriod()), Instant.now()));

        assertThat(client().post().uri("/payroll/{id}/close", calculated.getId())
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);
        var reloaded = repository.findById(calculated.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PayrollStatus.CALCULATED);
        assertThat(reloaded.getVersion()).isEqualTo(calculated.getVersion());
        assertThat(outboxRepository.findByPayrollId(calculated.getId()))
            .extracting(PayrollOutboxEvent::getId).containsExactly(existing.getId());
    }

    @Test
    void retriesFailedSummaryInsertAndIgnoresRedelivery() {
        var fixture = prepareCalculation();
        UUID payrollId = fixture.payrollId();
        RestClient http = client();
        String route = "/payroll/{id}/summary";
        assertThat(http.get().uri(route, UUID.randomUUID())
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(404);
        assertThat(http.get().uri(route, payrollId)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);
        assertThat(calculateStatus(fixture)).isEqualTo(200);
        http.post().uri("/payroll/{id}/close", payrollId).retrieve().toBodilessEntity();

        var pending = http.get().uri(route, payrollId).retrieve().toBodilessEntity();
        assertThat(pending.getStatusCode().value()).isEqualTo(202);
        assertThat(pending.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        assertThat(pending.getHeaders().getCacheControl()).isEqualTo("no-store");
        var event = outboxRepository.findByPayrollId(payrollId).getFirst();
        var message = new PayrollClosedMessage(event.getId(), event.getEventType(), event.getOccurredAt(),
            event.getPayload());

        String json = jsonMapper.writeValueAsString(message);
        jdbc.execute("ALTER TABLE tb_closed_payroll_summary ADD CONSTRAINT ck_test_reject_payroll "
            + "CHECK (payroll_id <> CAST('" + payrollId + "' AS UUID))");
        try {
            assertThatThrownBy(() -> closedConsumer.consume(json)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(summaryRepository.findById(payrollId)).isEmpty();
            assertThat(summaryRepository.existsByEventId(event.getId())).isFalse();
        } finally {
            jdbc.execute("ALTER TABLE tb_closed_payroll_summary DROP CONSTRAINT ck_test_reject_payroll");
        }

        closedConsumer.consume(json);
        closedConsumer.consume(json);

        var summary = summaryRepository.findById(payrollId).orElseThrow();
        assertThat(summary.getEventId()).isEqualTo(event.getId());
        assertThat(summary.getCompanyId()).isEqualTo(event.getPayload().companyId());
        assertThat(YearMonth.from(summary.getPeriod())).isEqualTo(event.getPayload().period());
        assertThat(summary.getClosedAt()).isEqualTo(event.getOccurredAt());
        assertThat(summary.getPayslipCount()).isEqualTo(2);
        assertThat(summary.getTotalEarnings()).isEqualByComparingTo("6000.00");
        assertThat(summary.getTotalDeductions()).isEqualByComparingTo("0.00");
        assertThat(summary.getNetPay()).isEqualByComparingTo("6000.00");

        var response = http.get().uri(route, payrollId)
            .retrieve().toEntity(PayrollDtos.SummaryResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = response.getBody();
        assertThat(body.period()).isEqualTo(event.getPayload().period());
        assertThat(body.payslipCount()).isEqualTo(2);
        assertThat(body.totalEarnings()).isEqualByComparingTo("6000.00");
        assertThat(body.totalDeductions()).isEqualByComparingTo("0.00");
        assertThat(body.netPay()).isEqualByComparingTo("6000.00");
    }

    @Test
    void generatesOnePdfAfterAStorageFailureAndRedelivery() {
        var fixture = prepareCalculation();
        assertThat(calculateStatus(fixture)).isEqualTo(200);
        client().post().uri("/payroll/{id}/close", fixture.payrollId()).retrieve().toBodilessEntity();
        UUID payslipId = payslipRepository.findIdsByPayrollId(fixture.payrollId()).getFirst();

        jdbc.execute("ALTER TABLE tb_payslip_document ADD CONSTRAINT ck_test_reject_document "
            + "CHECK (payslip_id <> CAST('" + payslipId + "' AS UUID))");
        try {
            assertThatThrownBy(() -> documentWorker.generate(payslipId.toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(documents.findById(payslipId)).isEmpty();
        } finally {
            jdbc.execute("ALTER TABLE tb_payslip_document DROP CONSTRAINT ck_test_reject_document");
        }

        documentWorker.generate(payslipId.toString());
        var pdf = documents.findById(payslipId).orElseThrow().getPdf();
        assertThat(new String(pdf, java.nio.charset.StandardCharsets.US_ASCII)).startsWith("%PDF-");

        documentWorker.generate(payslipId.toString());
        assertThat(documents.findById(payslipId).orElseThrow().getPdf()).isEqualTo(pdf);
    }

    @Test
    void returnsPersistedNetPayPerRegistrationForClosedPayrollOnly() {
        var fixture = prepareCalculation();
        UUID payrollId = fixture.payrollId();
        RestClient http = financeClient(payrollId);
        String route = "/payroll/{id}/finance-report";

        assertThat(http.get().uri(route, UUID.randomUUID())
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(404);
        assertThat(http.get().uri(route, payrollId)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);

        jdbc.update("UPDATE tb_attendance_summary SET absence_minutes = 60 WHERE contract_id = ?",
            fixture.firstContractId());
        assertThat(calculateStatus(fixture)).isEqualTo(200);
        assertThat(http.get().uri(route, payrollId)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);
        http.post().uri("/payroll/{id}/close", payrollId).retrieve().toBodilessEntity();

        var response = http.get().uri(route, payrollId).retrieve().toEntity(PayrollDtos.FinanceReport.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var report = response.getBody();
        assertThat(report.period()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(report.entries()).satisfiesExactly(
            first -> {
                assertThat(first.employeeName()).isEqualTo("Employee 001");
                assertThat(first.registrationNumber()).isEqualTo("001");
                assertThat(first.netPay()).isEqualByComparingTo("2985.00");
            },
            second -> {
                assertThat(second.employeeName()).isEqualTo("Employee 002");
                assertThat(second.registrationNumber()).isEqualTo("002");
                assertThat(second.netPay()).isEqualByComparingTo("3000.00");
            });
        assertThat(report.totalNetPay()).isEqualByComparingTo("5985.00");
    }

    @Test
    void exportsCsvWithAccentedNamesAndLeadingZerosOnlyAfterClosing(@TempDir Path output) throws Exception {
        var fixture = prepareCalculation("João da Conceição");
        UUID payrollId = fixture.payrollId();
        RestClient http = financeClient(payrollId);
        String route = "/payroll/{id}/finance-report.csv";

        assertThat(http.get().uri(route, UUID.randomUUID())
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(404);
        assertThat(http.get().uri(route, payrollId)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);

        jdbc.update("UPDATE tb_attendance_summary SET absence_minutes = 60 WHERE contract_id = ?",
            fixture.firstContractId());
        assertThat(calculateStatus(fixture)).isEqualTo(200);
        assertThat(http.get().uri(route, payrollId)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);
        http.post().uri("/payroll/{id}/close", payrollId).retrieve().toBodilessEntity();

        var csv = http.get().uri(route, payrollId).retrieve().toEntity(byte[].class);
        assertThat(csv.getStatusCode().value()).isEqualTo(200);
        assertThat(csv.getHeaders().getContentType()).isEqualTo(new MediaType("text", "csv", StandardCharsets.UTF_8));
        assertThat(csv.getHeaders().getContentDisposition().getFilename())
            .isEqualTo("finance-report-" + payrollId + ".csv");
        assertThat(csv.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(new String(csv.getBody(), StandardCharsets.UTF_8)).isEqualTo(
            "\uFEFFCompetência;Nome;Matrícula;Valor líquido\r\n"
                + "11/2026;\"João da Conceição 001\";\"001\";2985,00\r\n"
                + "11/2026;\"João da Conceição 002\";\"002\";3000,00\r\n"
                + "11/2026;TOTAL;;5985,00\r\n");
        Files.write(output.resolve("finance-report-verification.csv"), csv.getBody());
    }

    @Test
    void restrictsFinanceReportsToFinanceUsersWithCompanyAccess() {
        var fixture = prepareCalculation();
        UUID payrollId = fixture.payrollId();
        String company = repository.findById(payrollId).orElseThrow().getCompanyId().toString();
        assertThat(calculateStatus(fixture)).isEqualTo(200);
        client().post().uri("/payroll/{id}/close", payrollId).retrieve().toBodilessEntity();

        RestClient hr = authenticatedClient("HR", List.of(company));
        RestClient wrongCompany = authenticatedClient("FINANCE", List.of(UUID.randomUUID().toString()));
        RestClient malformedCompanies = authenticatedClient("FINANCE", company);
        RestClient authorized = financeClient(payrollId);
        for (String path : List.of("/payroll/{id}/finance-report", "/payroll/{id}/finance-report.csv")) {
            for (RestClient denied : List.of(hr, wrongCompany, malformedCompanies)) {
                assertThat(denied.get().uri(path, payrollId)
                    .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(403);
            }
            assertThat(authorized.get().uri(path, payrollId)
                .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(200);
        }
    }

    private static List<Integer> runConcurrently(Callable<Integer> request) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> synchronizedRequest = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent request start signal timed out.");
            }
            return request.call();
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(synchronizedRequest);
            var second = executor.submit(synchronizedRequest);
            boolean bothReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertThat(bothReady).isTrue();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }

    private int calculateStatus(CalculationFixture fixture) {
        return client().post().uri("/payroll/{id}/calculate", fixture.payrollId()).body(fixture.request())
            .exchange((sent, response) -> response.getStatusCode().value());
    }

    private CalculationFixture prepareCalculation() {
        return prepareCalculation("Employee");
    }

    private CalculationFixture prepareCalculation(String employeeNamePrefix) {
        UUID companyId = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 11);
        var start = month.atDay(1);
        var end = month.atEndOfMonth();
        var salaryPeriods = List.of(new PersonnelPayrollInput.SalaryPeriod(start, end, new BigDecimal("3000.00")));
        var contracts = List.of("001", "002").stream().map(registration ->
            new PersonnelPayrollInput.Contract(UUID.randomUUID(), UUID.randomUUID(), employeeNamePrefix + " " + registration,
                registration, start, null, start, end, 200, salaryPeriods)).toList();
        when(personnelClient.getPayrollInputs(companyId, month))
            .thenReturn(new PersonnelPayrollInput(companyId, month, contracts));

        RestClient http = client();
        var payroll = http.post().uri("/payroll")
            .body(new PayrollDtos.CreateRequest(companyId, month, PayrollType.REGULAR))
            .retrieve().body(PayrollDtos.Response.class);
        for (var contract : contracts) {
            http.post().uri("/attendance-summary")
                .body(new AttendanceSummaryDtos.Request(contract.contractId(), month, 12000L, 0L, 0L))
                .retrieve().toBodilessEntity();
        }

        var request = new PayrollDtos.CalculationRequest(
            createRubric(http, companyId, "SALARY", RubricType.EARNING),
            createRubric(http, companyId, "OVERTIME", RubricType.EARNING),
            createRubric(http, companyId, "ABSENCE", RubricType.DEDUCTION), new BigDecimal("1.50"));
        return new CalculationFixture(payroll.id(), contracts.getFirst().contractId(), contracts.getLast().contractId(),
            request);
    }

    private record CalculationFixture(
        UUID payrollId, UUID firstContractId, UUID secondContractId, PayrollDtos.CalculationRequest request
    ) {
    }

    private static UUID createRubric(RestClient http, UUID companyId, String code, RubricType type) {
        return http.post().uri("/rubric")
            .body(new RubricDtos.Request(companyId, code, code, type, false, false, false))
            .retrieve().body(RubricDtos.Response.class).id();
    }

    private RestClient financeClient(UUID payrollId) {
        UUID companyId = repository.findById(payrollId).orElseThrow().getCompanyId();
        return authenticatedClient("FINANCE", List.of(companyId.toString()));
    }

    private RestClient authenticatedClient(String role, Object companyIds) {
        String token = UUID.randomUUID().toString();
        when(jwtDecoder.decode(token)).thenReturn(Jwt.withTokenValue(token)
            .header("alg", "RS256")
            .subject("finance-test")
            .claim("realm_access", Map.of("roles", List.of(role)))
            .claim("company_ids", companyIds)
            .build());
        return client().mutate().defaultHeaders(headers -> headers.setBearerAuth(token)).build();
    }

    private RestClient client() {
        return RestClient.create("http://127.0.0.1:" + port);
    }
}
