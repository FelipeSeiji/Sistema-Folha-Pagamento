package com.payrollsevice.payroll_service.attendanceSummary.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.payrollsevice.payroll_service.attendanceSummary.dto.AttendanceSummaryDtos;
import com.payrollsevice.payroll_service.attendanceSummary.model.AttendanceSummary;
import com.payrollsevice.payroll_service.attendanceSummary.repository.AttendanceSummaryRepository;
import com.payrollsevice.payroll_service.attendanceSummary.service.AttendanceCompanyBackfill;
import com.payrollsevice.payroll_service.personnel.PersonnelClient;
import com.payrollsevice.payroll_service.personnel.PersonnelUnavailableException;

import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AttendanceSummaryControllerTests {
    @LocalServerPort
    private int port;

    @Autowired
    private AttendanceSummaryRepository repository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private PersonnelClient personnel;

    @Test
    void managesAttendanceForTheContractAndPeriod() {
        UUID companyId = UUID.randomUUID();
        RestClient client = finance(companyId);
        var request = new AttendanceSummaryDtos.Request(UUID.randomUUID(), YearMonth.of(2026, 11), 9600L, 60L, 0L);
        when(personnel.findContractCompanyId(request.contractId())).thenReturn(Optional.of(companyId));
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

        assertThat(client.delete().uri("/attendance-summary/{id}", body.id())
            .retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(204);
        assertThat(client.get().uri("/attendance-summary/{id}", body.id())
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(404);
    }

    @Test
    void restrictsCompaniesAndAvoidsPartialWritesWhenPersonnelFails() {
        UUID company = UUID.randomUUID();
        UUID otherCompany = UUID.randomUUID();
        UUID contract = UUID.randomUUID();
        UUID otherContract = UUID.randomUUID();
        var period = YearMonth.of(2026, 11);
        var request = new AttendanceSummaryDtos.Request(contract, period, 100L, 0L, 0L);
        when(jwtDecoder.decode("invalid-attendance-token")).thenThrow(new BadJwtException("Invalid test token."));
        assertThat(createStatus(client(), request)).isEqualTo(401);
        assertThat(createStatus(bearer("invalid-attendance-token"), request)).isEqualTo(401);

        doReturn(Optional.of(company)).when(personnel).findContractCompanyId(contract);
        assertThat(createStatus(finance(otherCompany), request)).isEqualTo(403);
        assertThat(repository.findByContractIdAndPeriod(contract, period.atDay(1))).isEmpty();

        when(personnel.findContractCompanyId(contract)).thenThrow(new PersonnelUnavailableException("Offline", null));
        assertThat(createStatus(finance(company), request)).isEqualTo(503);
        assertThat(repository.findByContractIdAndPeriod(contract, period.atDay(1))).isEmpty();

        doReturn(Optional.of(company)).when(personnel).findContractCompanyId(contract);
        when(personnel.findContractCompanyId(otherContract)).thenReturn(Optional.of(otherCompany));
        assertThat(createStatus(finance(company), request)).isEqualTo(201);
        var foreignRequest = new AttendanceSummaryDtos.Request(otherContract, period, 50L, 0L, 0L);
        assertThat(createStatus(finance(otherCompany), foreignRequest)).isEqualTo(201);
        var foreign = repository.findByContractIdAndPeriod(otherContract, period.atDay(1)).orElseThrow();
        assertThat(finance(company).get().uri("/attendance-summary/{id}", foreign.getId())
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(403);
        assertThat(finance(company).put().uri("/attendance-summary/{id}", foreign.getId()).body(foreignRequest)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(403);
        assertThat(finance(company).delete().uri("/attendance-summary/{id}", foreign.getId())
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(403);
        assertThat(repository.findById(foreign.getId())).isPresent();

        var page = finance(company).get().uri("/attendance-summary?size=1&sort=workedMinutes,asc")
            .retrieve().body(JsonNode.class);
        assertThat(page.path("page").path("totalElements").asInt()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("contractId").asString()).isEqualTo(contract.toString());
    }

    @Test
    void backfillsExistingRecordsWithoutReplacingTheirData() {
        UUID contract = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        var legacy = new AttendanceSummary();
        legacy.setContractId(contract);
        legacy.setPeriod(YearMonth.of(2025, 12).atDay(1));
        legacy.setWorkedMinutes(600);
        UUID id = repository.saveAndFlush(legacy).getId();

        assertThat(finance(company).get().uri("/attendance-summary/{id}", id)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(403);
        doReturn(Optional.of(company)).when(personnel).findContractCompanyId(contract);
        var backfill = new AttendanceCompanyBackfill(repository, personnel);
        backfill.run(null);
        backfill.run(null);

        var saved = repository.findById(id).orElseThrow();
        assertThat(saved.getCompanyId()).isEqualTo(company);
        assertThat(saved.getWorkedMinutes()).isEqualTo(600);
        assertThat(finance(company).get().uri("/attendance-summary/{id}", id)
            .retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(200);
    }

    private static int createStatus(RestClient http, AttendanceSummaryDtos.Request request) {
        return http.post().uri("/attendance-summary").body(request)
            .exchange((sent, response) -> response.getStatusCode().value());
    }

    private RestClient finance(UUID company) {
        String token = UUID.randomUUID().toString();
        when(jwtDecoder.decode(token)).thenReturn(Jwt.withTokenValue(token).header("alg", "RS256")
            .subject("attendance-test").claim("realm_access", Map.of("roles", List.of("FINANCE")))
            .claim("company_ids", List.of(company.toString())).build());
        return bearer(token);
    }

    private RestClient bearer(String token) {
        return client().mutate().defaultHeaders(headers -> headers.setBearerAuth(token)).build();
    }

    private RestClient client() {
        return RestClient.create("http://127.0.0.1:" + port);
    }
}
