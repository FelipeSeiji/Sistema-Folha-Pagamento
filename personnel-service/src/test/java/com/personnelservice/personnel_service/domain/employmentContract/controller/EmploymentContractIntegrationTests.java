package com.personnelservice.personnel_service.domain.employmentContract.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmploymentContractIntegrationTests {
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JwtDecoder decoder;

    @Test
    void registersAndReadsContractWithValidLinks() throws Exception {
        UUID company = UUID.randomUUID();
        UUID otherCompany = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        UUID otherDepartment = UUID.randomUUID();
        UUID position = UUID.randomUUID();
        String prefix = String.format(Locale.ROOT, "%08d", ThreadLocalRandom.current().nextInt(100_000_000));

        givenToken("hr", "HR", company);
        givenToken("hr-both", "HR", company, otherCompany);
        givenToken("hr-other", "HR", otherCompany);
        givenToken("hr-no-company", "HR");
        givenToken("finance", "FINANCE", company);
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid test token"));

        try (HttpClient client = HttpClient.newHttpClient()) {
            jdbc.update("INSERT INTO tb_company (id, cnpj, legal_name) VALUES (?, ?, ?), (?, ?, ?)",
                company, prefix + "S00142", "Test Company", otherCompany, prefix + "S00242", "Other Company");
            jdbc.update("INSERT INTO tb_department (id, name, company_id) VALUES (?, ?, ?)",
                department, "Finance", company);
            jdbc.update("INSERT INTO tb_department (id, name, company_id) VALUES (?, ?, ?)",
                otherDepartment, "Operations", otherCompany);
            jdbc.update("INSERT INTO tb_employee (id, cpf, name, email) VALUES (?, ?, ?, ?)",
                employee, prefix + "987", "Test Employee", "test@example.test");
            jdbc.update("INSERT INTO tb_job_position (id, name) VALUES (?, ?)", position, "Analyst");

            String contractRequest = """
                {"employeeId":"%s","companyId":"%s","departmentId":"%s","positionId":"%s",
                 "registrationNumber":"TEST-01","hireDate":"2026-01-01","monthlyWorkHours":160}
                """;
            String request = contractRequest.formatted(employee, company, department, position);
            assertThat(send(client, "/employment-contract", request, null).statusCode()).isEqualTo(401);
            assertThat(send(client, "/employment-contract", request, "invalid").statusCode()).isEqualTo(401);
            assertThat(send(client, "/employment-contract", request, "finance").statusCode()).isEqualTo(403);
            assertThat(send(client, "/employment-contract", request, "hr-other").statusCode()).isEqualTo(403);
            assertThat(send(client, "/employment-contract",
                contractRequest.formatted(employee, otherCompany, department, position), "hr-both").statusCode())
                .isEqualTo(400);
            assertThat(post(client, "/employment-contract",
                contractRequest.formatted(UUID.randomUUID(), company, department, position)).statusCode()).isEqualTo(404);
            var createdContract = post(client, "/employment-contract",
                contractRequest.formatted(employee, company, department, position));
            UUID contract = verifyCreatedAndReadable(client, createdContract, "/employment-contract", employee.toString());


            String details = "/employment-contract/" + contract;
            String endpoint = details + "/termination";
            String validTermination = "{\"terminationDate\":\"2026-03-31\"}";
            for (String path : List.of("/employment-contract", details, endpoint)) {
                String body = path.equals(endpoint) ? validTermination : null;
                assertThat(send(client, path, body, null).statusCode()).isEqualTo(401);
                assertThat(send(client, path, body, "invalid").statusCode()).isEqualTo(401);
                assertThat(send(client, path, body, "finance").statusCode()).isEqualTo(403);
            }
            assertThat(send(client, details, null, "hr-other").statusCode()).isEqualTo(403);
            assertThat(send(client, endpoint, validTermination, "hr-other").statusCode()).isEqualTo(403);
            assertThat(jdbc.queryForObject("SELECT termination_date FROM tb_employment_contract WHERE id = ?",
                java.time.LocalDate.class, contract)).isNull();

            var other = send(client, "/employment-contract",
                contractRequest.formatted(employee, otherCompany, otherDepartment, position), "hr-other");
            assertThat(other.statusCode()).isEqualTo(201);
            var list = send(client, "/employment-contract?size=1", null, "hr");
            assertThat(list.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<List<String>>read(list.body(), "$.content[*].id")).containsExactly(contract.toString());
            assertThat(JsonPath.<Integer>read(list.body(), "$.page.totalElements")).isEqualTo(1);
            var empty = send(client, "/employment-contract?size=1", null, "hr-no-company");
            assertThat(empty.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<Integer>read(empty.body(), "$.page.totalElements")).isZero();
            assertThat(post(client, endpoint, """
                {"terminationDate":"2025-12-31"}
                """).statusCode()).isEqualTo(400);
            jdbc.update("""
                INSERT INTO tb_salary_history (id, employment_contract_id, effective_from, salary)
                VALUES (?, ?, DATE '2026-01-01', 5000)
                """, UUID.randomUUID(), contract);
            String termination = """
                {"terminationDate":"2026-03-31"}
                """;
            var ended = post(client, endpoint, termination);
            assertThat(ended.statusCode()).isEqualTo(200);
            assertThat(ended.body()).contains("2026-03-31");
            assertThat(post(client, endpoint, termination).statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("""
                SELECT effective_until FROM tb_salary_history WHERE employment_contract_id = ?
                """, java.time.LocalDate.class, contract)).isEqualTo(java.time.LocalDate.parse("2026-03-31"));

            var deactivate = HttpRequest.newBuilder(uri("/job-position/" + position))
                .timeout(Duration.ofSeconds(10)).DELETE().build();
            assertThat(client.send(deactivate, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(204);
            assertThat(post(client, "/employment-contract",
                contractRequest.formatted(employee, company, department, position)).statusCode()).isEqualTo(409);
            var historical = client.send(HttpRequest.newBuilder(uri("/employment-contract/" + contract))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer hr").build(), HttpResponse.BodyHandlers.ofString());
            assertThat(historical.statusCode()).isEqualTo(200);
            assertThat(historical.body()).contains(position.toString());
        } finally {
            jdbc.update("""
                DELETE FROM tb_salary_history
                WHERE employment_contract_id IN (SELECT id FROM tb_employment_contract WHERE employee_id = ?)
                """, employee);
            jdbc.update("""
                DELETE FROM tb_salary_history
                WHERE employment_contract_id IN (SELECT id FROM tb_employment_contract WHERE employee_id = ?)
                """, employee);
            jdbc.update("DELETE FROM tb_employment_contract WHERE employee_id = ?", employee);
            jdbc.update("DELETE FROM tb_job_position WHERE id = ?", position);
            jdbc.update("DELETE FROM tb_department WHERE id IN (?, ?)", department, otherDepartment);
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

        var found = client.send(HttpRequest.newBuilder(location).timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer hr").build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(found.body()).isEqualTo(created.body());

        var missing = client.send(HttpRequest.newBuilder(uri(path + "/" + UUID.randomUUID()))
            .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer hr").build(), HttpResponse.BodyHandlers.discarding());
        assertThat(missing.statusCode()).isEqualTo(404);
        return id;
    }

    private void givenToken(String token, String role, UUID... companies) {
        when(decoder.decode(token)).thenReturn(Jwt.withTokenValue(token).header("alg", "RS256")
            .claim("realm_access", Map.of("roles", List.of(role)))
            .claim("company_ids", java.util.Arrays.stream(companies).map(UUID::toString).toList())
            .build());
    }

    private HttpResponse<String> post(HttpClient client, String path, String body) throws Exception {
        return send(client, path, body, "hr");
    }

    private HttpResponse<String> send(HttpClient client, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
