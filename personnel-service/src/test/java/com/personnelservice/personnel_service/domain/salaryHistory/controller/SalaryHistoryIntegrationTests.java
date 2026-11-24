package com.personnelservice.personnel_service.domain.salaryHistory.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SalaryHistoryIntegrationTests {
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registersAndReadsSalaryHistoryForExistingContract() throws Exception {
        UUID company = UUID.randomUUID();
        UUID otherCompany = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        UUID position = UUID.randomUUID();
        String prefix = String.format(Locale.ROOT, "%08d", ThreadLocalRandom.current().nextInt(100_000_000));

        try (HttpClient client = HttpClient.newHttpClient()) {
            jdbc.update("INSERT INTO tb_company (id, cnpj, legal_name) VALUES (?, ?, ?), (?, ?, ?)",
                company, prefix + "S00142", "Test Company", otherCompany, prefix + "S00242", "Other Company");
            jdbc.update("INSERT INTO tb_department (id, name, company_id) VALUES (?, ?, ?)",
                department, "Finance", company);
            jdbc.update("INSERT INTO tb_employee (id, cpf, name, email) VALUES (?, ?, ?, ?)",
                employee, prefix + "987", "Test Employee", "test@example.test");
            jdbc.update("INSERT INTO tb_job_position (id, name) VALUES (?, ?)", position, "Analyst");

            UUID contract = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO tb_employment_contract
                    (id, employee_id, company_id, department_id, job_position_id, registration_number,
                     hire_date, monthly_work_hours)
                VALUES (?, ?, ?, ?, ?, 'TEST-01', DATE '2026-01-01', 160)
                """, contract, employee, company, department, position);

            assertThat(post(client, "/salary-history", """
                {"contractId":"%s","effectiveFrom":"2026-02-01","effectiveUntil":"2026-01-01","salary":5000.00}
                """.formatted(contract)).statusCode()).isEqualTo(400);
            String salaryRequest = """
                {"contractId":"%s","effectiveFrom":"2026-01-01","salary":5000.00}
                """;
            assertThat(post(client, "/salary-history", salaryRequest.formatted(UUID.randomUUID()))
                .statusCode()).isEqualTo(404);
            verifyCreatedAndReadable(client, post(client, "/salary-history", salaryRequest.formatted(contract)),
                "/salary-history", contract.toString());
        } finally {
            jdbc.update("""
                DELETE FROM tb_salary_history
                WHERE employment_contract_id IN (SELECT id FROM tb_employment_contract WHERE employee_id = ?)
                """, employee);
            jdbc.update("DELETE FROM tb_employment_contract WHERE employee_id = ?", employee);
            jdbc.update("DELETE FROM tb_job_position WHERE id = ?", position);
            jdbc.update("DELETE FROM tb_department WHERE id = ?", department);
            jdbc.update("DELETE FROM tb_employee WHERE id = ?", employee);
            jdbc.update("DELETE FROM tb_company WHERE id IN (?, ?)", company, otherCompany);
        }
    }


    private UUID verifyCreatedAndReadable(HttpClient client, HttpResponse<String> created, String path, String value)
            throws Exception {
        assertThat(created.statusCode()).isEqualTo(201);
        URI location = URI.create(created.headers().firstValue("Location").orElseThrow());
        UUID id = UUID.fromString(location.getPath().substring(location.getPath().lastIndexOf('/') + 1));
        assertThat(location).isEqualTo(uri(path + "/" + id));
        assertThat(created.body()).contains(id.toString(), value);

        var found = client.send(HttpRequest.newBuilder(location).timeout(Duration.ofSeconds(10)).build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(found.body()).isEqualTo(created.body());

        var missing = client.send(HttpRequest.newBuilder(uri(path + "/" + UUID.randomUUID()))
            .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(missing.statusCode()).isEqualTo(404);
        return id;
    }

    private HttpResponse<String> post(HttpClient client, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
