package com.personnelservice.personnel_service.domain.employee.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmployeeIntegrationTests {
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsAndFindsEmployeeAndRejectsInvalidOrDuplicateRequests() throws Exception {
        String cpf = String.format(Locale.ROOT, "%011d", ThreadLocalRandom.current().nextLong(100_000_000_000L));
        URI endpoint = URI.create("http://127.0.0.1:" + port + "/employee");

        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(post(client, endpoint, "123", "employee@example.test").statusCode()).isEqualTo(400);
            assertThat(post(client, endpoint, cpf, "invalid-email").statusCode()).isEqualTo(400);

            var created = post(client, endpoint, cpf, "employee@example.test");
            assertThat(created.statusCode()).isEqualTo(201);
            UUID id = jdbc.queryForObject("SELECT id FROM tb_employee WHERE cpf = ?", UUID.class, cpf);
            URI location = URI.create(created.headers().firstValue("Location").orElseThrow());
            assertThat(location).isEqualTo(URI.create(endpoint + "/" + id));
            assertThat(created.body()).contains(id.toString(), cpf, "Employee Test", "employee@example.test");

            var found = client.send(HttpRequest.newBuilder(location).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(found.statusCode()).isEqualTo(200);
            assertThat(found.body()).isEqualTo(created.body());
            URI pageUri = URI.create("http://127.0.0.1:" + port + "/employee?page=0&size=1");
            var page = client.send(HttpRequest.newBuilder(pageUri).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<Integer>read(page.body(), "$.page.number")).isZero();
            assertThat(JsonPath.<Integer>read(page.body(), "$.page.size")).isEqualTo(1);
            assertThat(JsonPath.<Integer>read(page.body(), "$.content.length()")).isEqualTo(1);


            assertThat(post(client, endpoint, cpf, "other@example.test").statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_employee WHERE cpf = ?", Long.class, cpf))
                .isEqualTo(1L);

            var missing = client.send(HttpRequest.newBuilder(URI.create(endpoint + "/" + UUID.randomUUID()))
                .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.discarding());
            assertThat(missing.statusCode()).isEqualTo(404);
        } finally {
            jdbc.update("DELETE FROM tb_employee WHERE cpf = ?", cpf);
        }
    }

    private HttpResponse<String> post(HttpClient client, URI endpoint, String cpf, String email) throws Exception {
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"cpf\":\"" + cpf + "\",\"name\":\"Employee Test\",\"email\":\"" + email + "\"}"))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
