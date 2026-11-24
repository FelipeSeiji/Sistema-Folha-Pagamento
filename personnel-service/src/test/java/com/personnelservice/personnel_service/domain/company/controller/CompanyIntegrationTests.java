package com.personnelservice.personnel_service.domain.company.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompanyIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentRequestsWithTheSameCnpjCreateOnlyOneCompany() throws Exception {
        String prefix = String.format(Locale.ROOT, "%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        String cnpj = prefix + "T00142";
        String formatted = prefix.substring(0, 2) + "." + prefix.substring(2, 5) + "."
            + prefix.substring(5) + "/t001-42";

        try (HttpClient client = HttpClient.newHttpClient()) {
            var first = client.sendAsync(request(formatted), HttpResponse.BodyHandlers.discarding());
            var second = client.sendAsync(request(cnpj), HttpResponse.BodyHandlers.discarding());

            assertThat(new int[] {first.get(10, TimeUnit.SECONDS).statusCode(),
                second.get(10, TimeUnit.SECONDS).statusCode()}).containsExactlyInAnyOrder(201, 409);

            URI pageUri = URI.create("http://127.0.0.1:" + port + "/company?page=0&size=1");
            var page = client.send(HttpRequest.newBuilder(pageUri).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<Integer>read(page.body(), "$.page.number")).isZero();
            assertThat(JsonPath.<Integer>read(page.body(), "$.page.size")).isEqualTo(1);
            assertThat(JsonPath.<Integer>read(page.body(), "$.content.length()")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_company WHERE cnpj = ?", Long.class, cnpj))
                .isEqualTo(1L);

            UUID companyId = jdbc.queryForObject("SELECT id FROM tb_company WHERE cnpj = ?", UUID.class, cnpj);
            URI updateUri = URI.create("http://127.0.0.1:" + port + "/company/" + companyId);
            String updatedPayload = """
                {"legalName":"Updated Company"}
                """;
            var updated = put(client, updateUri, updatedPayload);
            assertThat(updated.statusCode()).isEqualTo(200);
            assertThat(updated.body()).contains("Updated Company", cnpj);

            assertThat(put(client, updateUri, """
                {"legalName":" "}
                """).statusCode()).isEqualTo(400);
            URI missingUpdateUri = URI.create("http://127.0.0.1:" + port + "/company/" + UUID.randomUUID());
            assertThat(put(client, missingUpdateUri, updatedPayload).statusCode()).isEqualTo(404);

            var persisted = client.send(HttpRequest.newBuilder(updateUri).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(persisted.statusCode()).isEqualTo(200);
            assertThat(persisted.body()).isEqualTo(updated.body());
        } finally {
            jdbc.update("DELETE FROM tb_company WHERE cnpj = ?", cnpj);
        }
    }

    private HttpRequest request(String cnpj) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/company"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"cnpj\":\"" + cnpj + "\",\"legalName\":\"Concurrency test\"}"))
            .build();
    }

    private HttpResponse<String> put(HttpClient client, URI uri, String body) throws Exception {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
