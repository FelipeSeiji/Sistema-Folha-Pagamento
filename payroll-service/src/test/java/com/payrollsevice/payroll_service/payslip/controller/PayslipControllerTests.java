package com.payrollsevice.payroll_service.payslip.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.payrollsevice.payroll_service.payslip.dto.PayslipDtos;
import com.payrollsevice.payroll_service.processing.model.Payroll;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.processing.model.PayrollType;
import com.payrollsevice.payroll_service.processing.repository.PayrollRepository;
import com.payrollsevice.payroll_service.rubric.dto.RubricDtos;
import com.payrollsevice.payroll_service.rubric.model.RubricType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PayslipControllerTests {
    @LocalServerPort
    private int port;

    @Autowired
    private PayrollRepository payrollRepository;

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

        RestClient client = RestClient.create("http://127.0.0.1:" + port);
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

        assertThat(deleteStatus(client, "/payslip/{id}", payslipId)).isEqualTo(409);
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

    private static int deleteStatus(RestClient client, String path, UUID id) {
        return client.delete().uri(path, id).exchange((sent, response) -> response.getStatusCode().value());
    }
}
