package com.payrollsevice.payroll_service.processing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.payrollsevice.payroll_service.attendanceSummary.dto.AttendanceSummaryDtos;
import com.payrollsevice.payroll_service.payslip.dto.PayslipDtos;
import com.payrollsevice.payroll_service.payslip.model.Payslip;
import com.payrollsevice.payroll_service.payslip.repository.PayslipRepository;
import com.payrollsevice.payroll_service.personnel.PersonnelClient;
import com.payrollsevice.payroll_service.personnel.PersonnelUnavailableException;
import com.payrollsevice.payroll_service.personnel.dto.PersonnelPayrollInput;
import com.payrollsevice.payroll_service.processing.dto.PayrollDtos;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.processing.model.PayrollType;
import com.payrollsevice.payroll_service.processing.repository.PayrollRepository;
import com.payrollsevice.payroll_service.rubric.dto.RubricDtos;
import com.payrollsevice.payroll_service.rubric.model.RubricType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PayrollControllerTests {
    @LocalServerPort
    private int port;

    @MockitoBean
    private PersonnelClient personnelClient;

    @Autowired
    private PayrollRepository repository;

    @Autowired
    private PayslipRepository payslipRepository;

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
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> calculate = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Calculation start signal timed out.");
            }
            return calculateStatus(fixture);
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(calculate);
            var second = executor.submit(calculate);
            boolean bothReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertThat(bothReady).isTrue();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
        }

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

    private int calculateStatus(CalculationFixture fixture) {
        return client().post().uri("/payroll/{id}/calculate", fixture.payrollId()).body(fixture.request())
            .exchange((sent, response) -> response.getStatusCode().value());
    }

    private CalculationFixture prepareCalculation() {
        UUID companyId = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 11);
        var start = month.atDay(1);
        var end = month.atEndOfMonth();
        var salaryPeriods = List.of(new PersonnelPayrollInput.SalaryPeriod(start, end, new BigDecimal("3000.00")));
        var contracts = List.of("001", "002").stream().map(registration ->
            new PersonnelPayrollInput.Contract(UUID.randomUUID(), UUID.randomUUID(), "Employee " + registration,
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

    private RestClient client() {
        return RestClient.create("http://127.0.0.1:" + port);
    }
}
