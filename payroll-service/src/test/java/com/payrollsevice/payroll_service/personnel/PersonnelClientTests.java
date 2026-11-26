package com.payrollsevice.payroll_service.personnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.payrollsevice.payroll_service.personnel.dto.PersonnelPayrollInput.SalaryPeriod;

class PersonnelClientTests {
    private static final String BASE_URL = "http://personnel.test";
    private static final UUID COMPANY = UUID.randomUUID();
    private static final YearMonth PERIOD = YearMonth.of(2026, 11);
    private static final String INPUT_URL = BASE_URL + "/internal/payroll-input?companyId=" + COMPANY
        + "&period=" + PERIOD;

    private MockRestServiceServer server;
    private PersonnelClient client;

    @BeforeEach
    void configureClient() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PersonnelClient(builder, BASE_URL);
    }

    @Test
    void readsTwoSalaryPeriodsForTheRequestedMonth() {
        UUID contract = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        server.expect(requestTo(INPUT_URL)).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {"companyId":"%s","period":"2026-11","contracts":[
                  {"contractId":"%s","employeeId":"%s","employeeName":"Employee","registrationNumber":"001",
                   "hireDate":"2026-11-01","terminationDate":null,"periodStart":"2026-11-01",
                   "periodEnd":"2026-11-30","monthlyWorkHours":160,"salaryPeriods":[
                    {"from":"2026-11-01","until":"2026-11-14","salary":5000.00},
                    {"from":"2026-11-15","until":"2026-11-30","salary":6000.00}
                   ]}
                ]}
                """.formatted(COMPANY, contract, employee), MediaType.APPLICATION_JSON));

        var input = client.getPayrollInputs(COMPANY, PERIOD);
        assertThat(input.companyId()).isEqualTo(COMPANY);
        assertThat(input.period()).isEqualTo(PERIOD);
        assertThat(input.contracts()).singleElement().satisfies(record -> {
            assertThat(record.contractId()).isEqualTo(contract);
            assertThat(record.employeeId()).isEqualTo(employee);
            assertThat(record.monthlyWorkHours()).isEqualTo(160);
            assertThat(record.salaryPeriods()).containsExactly(
                new SalaryPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14), new BigDecimal("5000.00")),
                new SalaryPeriod(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 30), new BigDecimal("6000.00")));
        });
        server.verify();
    }

    @Test
    void propagatesSalaryConflictAsADistinctFailure() {
        server.expect(requestTo(INPUT_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.getPayrollInputs(COMPANY, PERIOD))
            .isInstanceOf(PersonnelInputConflictException.class);
        server.verify();
    }

    @Test
    void failsWhenPersonnelCannotBeReached() {
        server.expect(requestTo(INPUT_URL)).andRespond(withException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> client.getPayrollInputs(COMPANY, PERIOD))
            .isInstanceOf(PersonnelUnavailableException.class)
            .hasRootCauseInstanceOf(ConnectException.class);
        server.verify();
    }

    @Test
    void resolvesCompanyFromThePersonnelContractResponse() {
        UUID contract = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/employment-contract/" + contract)).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {"id":"%s","companyId":"%s","registrationNumber":"001"}
                """.formatted(contract, COMPANY), MediaType.APPLICATION_JSON));

        assertThat(client.findContractCompanyId(contract)).contains(COMPANY);
        server.verify();
    }
}
