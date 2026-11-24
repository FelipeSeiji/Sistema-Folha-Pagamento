package com.personnelservice.personnel_service.domain.payrollInput.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.support.TransactionTemplate;

import com.personnelservice.personnel_service.domain.company.model.Company;
import com.personnelservice.personnel_service.domain.department.model.Department;
import com.personnelservice.personnel_service.domain.employee.model.Employee;
import com.personnelservice.personnel_service.domain.employmentContract.model.EmploymentContract;
import com.personnelservice.personnel_service.domain.jobPosition.model.JobPosition;
import com.personnelservice.personnel_service.domain.salaryHistory.model.SalaryHistory;

import jakarta.persistence.EntityManager;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PayrollInputIntegrationTests {
    @LocalServerPort
    private int port;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private EntityManager entities;

    @Test
    void returnsHistoricalSegmentsAndRejectsInconsistentCoverage() throws Exception {
        String suffix = String.format(java.util.Locale.ROOT, "%011d",
            java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000_000L));
        var fixture = transactions.execute(status -> createFixture(suffix));
        UUID company = fixture.companyId();
        UUID contract = fixture.contractId();

        try (HttpClient client = HttpClient.newHttpClient()) {

            var result = get(client, company, "2026-11");
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<String>read(result.body(), "$.period")).isEqualTo("2026-11");
            assertThat(JsonPath.<String>read(result.body(), "$.contracts[0].contractId")).isEqualTo(contract.toString());
            assertThat(JsonPath.<java.util.List<String>>read(result.body(), "$.contracts[0].salaryPeriods[*].from"))
                .containsExactly("2026-11-05", "2026-11-15");
            assertThat(JsonPath.<java.util.List<String>>read(result.body(), "$.contracts[0].salaryPeriods[*].until"))
                .containsExactly("2026-11-14", "2026-11-25");
            assertThat(JsonPath.<java.util.List<Number>>read(result.body(), "$.contracts[0].salaryPeriods[*].salary"))
                .extracting(Number::intValue).containsExactly(5000, 6000);

            var empty = get(client, company, "2026-12");
            assertThat(empty.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<Integer>read(empty.body(), "$.contracts.length()")).isZero();
            assertThat(get(client, UUID.randomUUID(), "2026-11").statusCode()).isEqualTo(404);
            assertThat(get(client, company, "2026-13").statusCode()).isEqualTo(400);

            moveSecondSalary(fixture, LocalDate.of(2026, 11, 16));
            assertThat(get(client, company, "2026-11").statusCode()).isEqualTo(409);
            moveSecondSalary(fixture, LocalDate.of(2026, 11, 14));
            assertThat(get(client, company, "2026-11").statusCode()).isEqualTo(409);
        } finally {
            transactions.executeWithoutResult(status -> {
                entities.remove(entities.find(SalaryHistory.class, fixture.firstSalaryId()));
                entities.remove(entities.find(SalaryHistory.class, fixture.secondSalaryId()));
                entities.remove(entities.find(EmploymentContract.class, fixture.contractId()));
                entities.remove(entities.find(Department.class, fixture.departmentId()));
                entities.remove(entities.find(JobPosition.class, fixture.positionId()));
                entities.remove(entities.find(Employee.class, fixture.employeeId()));
                entities.remove(entities.find(Company.class, fixture.companyId()));
            });
        }
    }

    private Fixture createFixture(String suffix) {
        var company = new Company(null, suffix + "P42", "Payroll test", false);
        var department = new Department(null, "Finance", company, true);
        var position = new JobPosition(null, "Analyst", null, true);
        var employee = new Employee(null, suffix, "Test", "test@example.test", false);
        entities.persist(company);
        entities.persist(department);
        entities.persist(position);
        entities.persist(employee);

        var contract = new EmploymentContract();
        contract.setEmployee(employee);
        contract.setCompany(company);
        contract.setDepartment(department);
        contract.setPosition(position);
        contract.setRegistrationNumber("TEST");
        contract.setHireDate(LocalDate.of(2026, 11, 5));
        contract.setTerminationDate(LocalDate.of(2026, 11, 25));
        contract.setMonthlyWorkHours(160);
        entities.persist(contract);

        var first = persistSalary(contract, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 14), 5000);
        var second = persistSalary(contract, LocalDate.of(2026, 11, 15), null, 6000);
        return new Fixture(company.getId(), department.getId(), position.getId(), employee.getId(),
            contract.getId(), first.getId(), second.getId());
    }

    private SalaryHistory persistSalary(EmploymentContract contract, LocalDate from, LocalDate until, int amount) {
        var history = new SalaryHistory();
        history.setContract(contract);
        history.setEffectiveFrom(from);
        history.setEffectiveUntil(until);
        history.setSalary(BigDecimal.valueOf(amount));
        entities.persist(history);
        return history;
    }

    private void moveSecondSalary(Fixture fixture, LocalDate from) {
        transactions.executeWithoutResult(status ->
            entities.find(SalaryHistory.class, fixture.secondSalaryId()).setEffectiveFrom(from));
    }

    private record Fixture(
        UUID companyId, UUID departmentId, UUID positionId, UUID employeeId,
        UUID contractId, UUID firstSalaryId, UUID secondSalaryId
    ) {
    }

    private HttpResponse<String> get(HttpClient client, UUID company, String period) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + port + "/internal/payroll-input?companyId=" + company
            + "&period=" + period);
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
