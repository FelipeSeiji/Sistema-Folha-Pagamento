package com.personnelservice.personnel_service.domain.jobPosition.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobPositionIntegrationTests {
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registersAndReadsPosition() throws Exception {
        UUID position = null;
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(post(client, "/job-position", """
                {"name":""}
                """).statusCode()).isEqualTo(400);
            var created = post(client, "/job-position", """
                {"name":"Reviewer","description":"Test position"}
                """);
            assertThat(created.statusCode()).isEqualTo(201);
            URI location = URI.create(created.headers().firstValue("Location").orElseThrow());
            position = UUID.fromString(location.getPath().substring(location.getPath().lastIndexOf('/') + 1));
            verifyCreatedAndReadable(client, created, "/job-position", "Reviewer");
            URI pageUri = URI.create("http://127.0.0.1:" + port + "/job-position?page=0&size=1");
            var page = client.send(HttpRequest.newBuilder(pageUri).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<Integer>read(page.body(), "$.page.number")).isZero();
            assertThat(JsonPath.<Integer>read(page.body(), "$.page.size")).isEqualTo(1);
            assertThat(JsonPath.<Integer>read(page.body(), "$.content.length()")).isEqualTo(1);


            URI updateUri = uri("/job-position/" + position);
            String updatedPayload = """
                {"name":"Updated Position","description":"Updated description"}
                """;
            var updated = put(client, updateUri, updatedPayload);
            assertThat(updated.statusCode()).isEqualTo(200);
            assertThat(updated.body()).contains("Updated Position", "Updated description");

            assertThat(put(client, updateUri, """
                {"name":" "}
                """).statusCode()).isEqualTo(400);
            URI missingUpdateUri = URI.create("http://127.0.0.1:" + port + "/job-position/" + UUID.randomUUID());
            assertThat(put(client, missingUpdateUri, updatedPayload).statusCode()).isEqualTo(404);

            var persisted = client.send(HttpRequest.newBuilder(updateUri).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(persisted.statusCode()).isEqualTo(200);
            assertThat(persisted.body()).isEqualTo(updated.body());

            var deactivate = HttpRequest.newBuilder(updateUri).timeout(Duration.ofSeconds(10)).DELETE().build();
            assertThat(client.send(deactivate, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(204);
            assertThat(client.send(deactivate, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(204);
            var historical = client.send(HttpRequest.newBuilder(updateUri).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(historical.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<Boolean>read(historical.body(), "$.active")).isFalse();
            assertThat(put(client, updateUri, updatedPayload).statusCode()).isEqualTo(409);

            var activePage = client.send(HttpRequest.newBuilder(pageUri).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(activePage.statusCode()).isEqualTo(200);
            assertThat(JsonPath.<java.util.List<String>>read(activePage.body(), "$.content[*].id"))
                .doesNotContain(position.toString());
            assertThat(jdbc.queryForObject("SELECT active FROM tb_job_position WHERE id = ?", Boolean.class, position))
                .isFalse();
        } finally {
            if (position != null) {
                jdbc.update("DELETE FROM tb_job_position WHERE id = ?", position);
            }
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

    private HttpResponse<String> put(HttpClient client, URI uri, String body) throws Exception {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
