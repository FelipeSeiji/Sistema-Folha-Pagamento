package com.payrollsevice.payroll_service.attendanceSummary.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.payrollsevice.payroll_service.attendanceSummary.dto.AttendanceSummaryDtos;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AttendanceSummaryControllerTests {
    @LocalServerPort
    private int port;

    @Test
    void createsReadsAndUpdatesAttendanceForTheContractAndPeriod() {
        RestClient client = RestClient.create("http://127.0.0.1:" + port);
        var request = new AttendanceSummaryDtos.Request(UUID.randomUUID(), YearMonth.of(2026, 11), 9600L, 60L, 0L);
        var created = client.post().uri("/attendance-summary").body(request)
            .retrieve().toEntity(AttendanceSummaryDtos.Response.class);

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        var body = created.getBody();
        assertThat(body.period()).isEqualTo(request.period());
        assertThat(created.getHeaders().getLocation().getPath()).isEqualTo("/attendance-summary/" + body.id());
        assertThat(client.get().uri("/attendance-summary/{id}", body.id()).retrieve()
            .body(AttendanceSummaryDtos.Response.class)).isEqualTo(body);
        assertThat(client.get().uri("/attendance-summary").retrieve().body(String.class)).contains(body.id().toString());
        assertThat(client.post().uri("/attendance-summary").body(request)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);

        var update = new AttendanceSummaryDtos.Request(request.contractId(), request.period(), 9300L, 0L, 300L);
        var updated = client.put().uri("/attendance-summary/{id}", body.id()).body(update)
            .retrieve().body(AttendanceSummaryDtos.Response.class);
        assertThat(updated.absenceMinutes()).isEqualTo(300L);
        assertThat(client.get().uri("/attendance-summary/{id}", body.id()).retrieve()
            .body(AttendanceSummaryDtos.Response.class)).isEqualTo(updated);
    }
}
