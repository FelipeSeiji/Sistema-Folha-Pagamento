package com.personnelservice.personnel_service.domain.department.controller;

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
class DepartmentIntegrationTests {
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsAndFindsDepartmentOnlyWhenCompanyExists() throws Exception {
        UUID companyId = UUID.randomUUID();
        String cnpj = String.format(Locale.ROOT, "%08dD00142", ThreadLocalRandom.current().nextInt(100_000_000));
        URI endpoint = URI.create("http://127.0.0.1:" + port + "/department");

        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(create(client, endpoint, companyId, "Finance").statusCode()).isEqualTo(404);

            jdbc.update("INSERT INTO tb_company (id, cnpj, legal_name) VALUES (?, ?, ?)",
                companyId, cnpj, "Department test");

            assertThat(create(client, endpoint, companyId, " ").statusCode()).isEqualTo(400);
            var created = create(client, endpoint, companyId, "Finance");
            assertThat(created.statusCode()).isEqualTo(201);
            assertThat(created.body()).contains("Finance", companyId.toString());

            UUID departmentId = jdbc.queryForObject(
                "SELECT id FROM tb_department WHERE company_id = ? AND name = ?", UUID.class, companyId, "Finance");
            URI location = URI.create(created.headers().firstValue("Location").orElseThrow());
            assertThat(location).isEqualTo(URI.create(endpoint + "/" + departmentId));

            var found = client.send(HttpRequest.newBuilder(location).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(found.statusCode()).isEqualTo(200);
            assertThat(found.body()).isEqualTo(created.body());
            URI pageUri = URI.create("http://127.0.0.1:" + port + "/department?companyId=" + companyId + "&page=0&size=1");
            var page = client.send(HttpRequest.newBuilder(pageUri).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<Integer>read(page.body(), "$.page.number")).isZero();
            assertThat(JsonPath.<Integer>read(page.body(), "$.page.size")).isEqualTo(1);
            assertThat(JsonPath.<java.util.List<String>>read(page.body(), "$.content[*].id"))
                .containsExactly(departmentId.toString());
            URI emptyUri = URI.create("http://127.0.0.1:" + port + "/department?companyId=" + UUID.randomUUID());
            var empty = client.send(HttpRequest.newBuilder(emptyUri).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(empty.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<Integer>read(empty.body(), "$.page.totalElements")).isZero();


            var missing = client.send(HttpRequest.newBuilder(URI.create(endpoint + "/" + UUID.randomUUID()))
                .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.discarding());
            assertThat(missing.statusCode()).isEqualTo(404);

            URI updateUri = location;
            String updatedPayload = """
                {"name":"Updated Department"}
                """;
            var updated = put(client, updateUri, updatedPayload);
            assertThat(updated.statusCode()).isEqualTo(200);
            assertThat(updated.body()).contains("Updated Department", companyId.toString());

            assertThat(put(client, updateUri, """
                {"name":" "}
                """).statusCode()).isEqualTo(400);
            URI missingUpdateUri = URI.create("http://127.0.0.1:" + port + "/department/" + UUID.randomUUID());
            assertThat(put(client, missingUpdateUri, updatedPayload).statusCode()).isEqualTo(404);

            var persisted = client.send(HttpRequest.newBuilder(updateUri).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(persisted.statusCode()).isEqualTo(200);
            assertThat(persisted.body()).isEqualTo(updated.body());
        } finally {
            jdbc.update("DELETE FROM tb_department WHERE company_id = ?", companyId);
            jdbc.update("DELETE FROM tb_company WHERE id = ?", companyId);
        }
    }

    private HttpResponse<String> create(HttpClient client, URI endpoint, UUID companyId, String name) throws Exception {
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"companyId\":\"" + companyId + "\",\"name\":\"" + name + "\"}"))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(HttpClient client, URI uri, String body) throws Exception {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
