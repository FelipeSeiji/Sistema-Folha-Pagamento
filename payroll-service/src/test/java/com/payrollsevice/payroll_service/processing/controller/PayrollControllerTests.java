package com.payrollsevice.payroll_service.processing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.payrollsevice.payroll_service.personnel.PersonnelClient;
import com.payrollsevice.payroll_service.personnel.PersonnelUnavailableException;
import com.payrollsevice.payroll_service.personnel.dto.PersonnelPayrollInput;
import com.payrollsevice.payroll_service.processing.dto.PayrollDtos;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.processing.model.PayrollType;
import com.payrollsevice.payroll_service.processing.repository.PayrollRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PayrollControllerTests {
    @LocalServerPort
    private int port;

    @MockitoBean
    private PersonnelClient personnelClient;

    @Autowired
    private PayrollRepository repository;

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

    private RestClient client() {
        return RestClient.create("http://127.0.0.1:" + port);
    }
}
