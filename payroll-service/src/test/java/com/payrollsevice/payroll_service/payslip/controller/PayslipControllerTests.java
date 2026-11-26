package com.payrollsevice.payroll_service.payslip.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

import com.payrollsevice.payroll_service.payslip.dto.PayslipDtos;
import com.payrollsevice.payroll_service.payslip.model.Payslip;
import com.payrollsevice.payroll_service.payslip.repository.PayslipRepository;
import com.payrollsevice.payroll_service.processing.model.Payroll;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.processing.model.PayrollType;
import com.payrollsevice.payroll_service.processing.repository.PayrollRepository;
import com.payrollsevice.payroll_service.rubric.dto.RubricDtos;
import com.payrollsevice.payroll_service.rubric.model.RubricType;

import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PayslipControllerTests {
    @LocalServerPort
    private int port;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void managesPayslipAndProtectsReferencedRecords() {
        UUID companyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        LocalDate period = LocalDate.of(2026, 11, 1);
        Payroll payroll = new Payroll();
        payroll.setCompanyId(companyId);
        payroll.setPeriod(period);
        payroll.setType(PayrollType.REGULAR);
        payroll.setSnapshotCapturedAt(Instant.now());
        payroll.setSnapshotPayload(new Payroll.Snapshot(List.of(new Payroll.Snapshot.Contract(
            contractId, UUID.randomUUID(), "Jane Doe", "001", period, null, period,
            period.plusMonths(1).minusDays(1), 220, List.of()))));
        payrollRepository.saveAndFlush(payroll);

        RestClient client = authenticated("FINANCE", List.of(companyId.toString()));
        var earning = new RubricDtos.Request(companyId, "001", "Salary", RubricType.EARNING, false, false, false);
        var deduction = new RubricDtos.Request(companyId, "002", "Deduction", RubricType.DEDUCTION, false, false, false);
        var salary = client.post().uri("/rubric").body(earning).retrieve().body(RubricDtos.Response.class);
        var discount = client.post().uri("/rubric").body(deduction).retrieve().body(RubricDtos.Response.class);
        assertThat(client.post().uri("/rubric").body(earning)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);
        assertThat(client.get().uri("/rubric/{id}", salary.id()).retrieve().body(RubricDtos.Response.class))
            .isEqualTo(salary);

        var request = new PayslipDtos.Request(payroll.getId(), contractId, new BigDecimal("3000.00"), List.of(
            new PayslipDtos.ItemRequest(salary.id(), null, new BigDecimal("3000.00")),
            new PayslipDtos.ItemRequest(discount.id(), null, new BigDecimal("100.00"))));
        when(jwtDecoder.decode("invalid-payslip-write-token")).thenThrow(new BadJwtException("Invalid test token."));
        var unauthenticated = List.of(client(), bearer("invalid-payslip-write-token"));
        var forbidden = List.of(authenticated("HR", List.of(companyId.toString())),
            authenticated("FINANCE", List.of(UUID.randomUUID().toString())));
        for (RestClient caller : unauthenticated) {
            assertThat(createStatus(caller, request)).isEqualTo(401);
        }
        for (RestClient caller : forbidden) {
            assertThat(createStatus(caller, request)).isEqualTo(403);
        }
        assertThat(payslipRepository.findByPayrollId(payroll.getId())).isEmpty();

        var created = client.post().uri("/payslip").body(request).retrieve().toEntity(PayslipDtos.Response.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        UUID payslipId = created.getBody().id();
        assertThat(created.getHeaders().getLocation().getPath()).isEqualTo("/payslip/" + payslipId);
        var saved = client.get().uri("/payslip/{id}", payslipId).retrieve().body(PayslipDtos.Response.class);
        assertThat(saved.netPay()).isEqualByComparingTo("2900.00");
        assertThat(saved.items()).extracting(PayslipDtos.ItemResponse::rubricId)
            .containsExactly(salary.id(), discount.id());
        assertThat(client.get().uri("/payslip").retrieve().body(String.class)).contains(payslipId.toString());
        assertThat(client.post().uri("/payslip").body(request)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);

        var updatedRubric = new RubricDtos.Request(companyId, "001", "Updated Salary", RubricType.EARNING,
            false, false, false);
        assertThat(client.put().uri("/rubric/{id}", salary.id()).body(updatedRubric)
            .retrieve().body(RubricDtos.Response.class).description()).isEqualTo("Updated Salary");

        var update = new PayslipDtos.Request(payroll.getId(), contractId, new BigDecimal("3100.00"), List.of(
            new PayslipDtos.ItemRequest(salary.id(), null, new BigDecimal("3100.00"))));
        for (RestClient caller : unauthenticated) {
            assertThat(updateStatus(caller, payslipId, update)).isEqualTo(401);
            assertThat(deleteStatus(caller, "/payslip/{id}", payslipId)).isEqualTo(401);
        }
        for (RestClient caller : forbidden) {
            assertThat(updateStatus(caller, payslipId, update)).isEqualTo(403);
            assertThat(deleteStatus(caller, "/payslip/{id}", payslipId)).isEqualTo(403);
        }
        assertThat(client.get().uri("/payslip/{id}", payslipId).retrieve().body(PayslipDtos.Response.class))
            .isEqualTo(saved);

        var updated = client.put().uri("/payslip/{id}", payslipId).body(update)
            .retrieve().body(PayslipDtos.Response.class);
        assertThat(updated.netPay()).isEqualByComparingTo("3100.00");

        var reloaded = client.get().uri("/payslip/{id}", payslipId)
            .retrieve().body(PayslipDtos.Response.class);
        assertThat(reloaded.items()).extracting(PayslipDtos.ItemResponse::appliedDescription)
            .containsExactly("Updated Salary");

        payroll.setStatus(PayrollStatus.CLOSED);
        payroll = payrollRepository.saveAndFlush(payroll);
        assertThat(client.put().uri("/payslip/{id}", payslipId).body(request)
            .<Integer>exchange((sent, response) -> response.getStatusCode().value())).isEqualTo(409);

        assertThat(createStatus(client, request)).isEqualTo(409);
        assertThat(deleteStatus(client, "/payslip/{id}", payslipId)).isEqualTo(409);
        assertThat(client.get().uri("/payslip/{id}", payslipId).retrieve().body(PayslipDtos.Response.class))
            .isEqualTo(reloaded);
        assertThat(deleteStatus(client, "/payroll/{id}", payroll.getId())).isEqualTo(409);

        payroll.setStatus(PayrollStatus.DRAFT);
        payrollRepository.saveAndFlush(payroll);
        assertThat(deleteStatus(client, "/payroll/{id}", payroll.getId())).isEqualTo(409);
        assertThat(deleteStatus(client, "/rubric/{id}", salary.id())).isEqualTo(409);
        assertThat(deleteStatus(client, "/payslip/{id}", payslipId)).isEqualTo(204);
        assertThat(deleteStatus(client, "/payslip/{id}", payslipId)).isEqualTo(404);
        assertThat(deleteStatus(client, "/rubric/{id}", salary.id())).isEqualTo(204);
        assertThat(deleteStatus(client, "/payroll/{id}", payroll.getId())).isEqualTo(204);
    }

    @Test
    void restrictsPayslipReadsAndFiltersCompaniesBeforePagination() {
        UUID firstCompany = UUID.randomUUID();
        UUID secondCompany = UUID.randomUUID();
        UUID first = persistPayslip(firstCompany, "200.00");
        UUID second = persistPayslip(secondCompany, "300.00");
        UUID outside = persistPayslip(UUID.randomUUID(), "100.00");
        List<String> companies = List.of(firstCompany.toString(), secondCompany.toString());

        RestClient anonymous = client();
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid test token"));
        RestClient invalid = bearer("invalid-token");
        RestClient hr = authenticated("HR", companies);
        RestClient finance = authenticated("FINANCE", companies);

        for (String route : List.of("/payslip", "/payslip/" + first)) {
            assertThat(getStatus(anonymous, route)).isEqualTo(401);
            assertThat(getStatus(invalid, route)).isEqualTo(401);
            assertThat(getStatus(hr, route)).isEqualTo(403);
        }
        assertThat(anonymous.head().uri("/payslip/{id}", first)
            .<Integer>exchange((sent, result) -> result.getStatusCode().value())).isEqualTo(401);
        assertThat(getStatus(finance, "/payslip/" + outside)).isEqualTo(403);
        assertThat(getStatus(finance, "/payslip/" + first)).isEqualTo(200);

        for (int page = 0; page < 2; page++) {
            JsonNode result = finance.get().uri("/payslip?size=1&sort=netPay,asc&page={page}", page)
                .retrieve().body(JsonNode.class);
            assertThat(result.path("content").size()).isEqualTo(1);
            assertThat(result.path("content").get(0).path("id").asString())
                .isEqualTo(List.of(first, second).get(page).toString());
            assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(2);
        }

        var empty = authenticated("FINANCE", List.of("invalid-uuid")).get().uri("/payslip")
            .retrieve().body(JsonNode.class);
        assertThat(empty.path("content").size()).isZero();
        assertThat(empty.path("page").path("totalElements").asInt()).isZero();
    }

    private UUID persistPayslip(UUID companyId, String amount) {
        Payroll payroll = new Payroll();
        payroll.setCompanyId(companyId);
        payroll.setPeriod(LocalDate.of(2026, 11, 1));
        payroll.setType(PayrollType.REGULAR);
        payroll.setSnapshotCapturedAt(Instant.now());
        payroll.setSnapshotPayload(new Payroll.Snapshot(List.of()));
        payrollRepository.saveAndFlush(payroll);

        Payslip payslip = new Payslip();
        payslip.setPayroll(payroll);
        payslip.setContractId(UUID.randomUUID());
        payslip.setAppliedBaseSalary(new BigDecimal(amount));
        payslip.setTotalEarnings(new BigDecimal(amount));
        payslip.setTotalDeductions(BigDecimal.ZERO);
        payslip.setNetPay(new BigDecimal(amount));
        return payslipRepository.saveAndFlush(payslip).getId();
    }

    private static int getStatus(RestClient client, String route) {
        return client.get().uri(route).exchange((sent, result) -> result.getStatusCode().value());
    }

    private RestClient authenticated(String role, List<String> companies) {
        String token = UUID.randomUUID().toString();
        when(jwtDecoder.decode(token)).thenReturn(Jwt.withTokenValue(token).header("alg", "RS256")
            .subject("payslip-test").claim("realm_access", Map.of("roles", List.of(role)))
            .claim("company_ids", companies).build());
        return bearer(token);
    }

    private RestClient bearer(String token) {
        return client().mutate().defaultHeaders(headers -> headers.setBearerAuth(token)).build();
    }

    private RestClient client() {
        return RestClient.create("http://127.0.0.1:" + port);
    }

    private static int createStatus(RestClient client, PayslipDtos.Request request) {
        return client.post().uri("/payslip").body(request).exchange((sent, response) -> response.getStatusCode().value());
    }

    private static int updateStatus(RestClient client, UUID id, PayslipDtos.Request request) {
        return client.put().uri("/payslip/{id}", id).body(request)
            .exchange((sent, response) -> response.getStatusCode().value());
    }

    private static int deleteStatus(RestClient client, String path, UUID id) {
        return client.delete().uri(path, id).exchange((sent, response) -> response.getStatusCode().value());
    }
}
