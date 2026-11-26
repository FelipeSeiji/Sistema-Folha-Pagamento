package com.payrollsevice.payroll_service.processing.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.payrollsevice.payroll_service.payslip.document.PayslipDocumentDispatcher;
import com.payrollsevice.payroll_service.payslip.dto.PayslipDtos;
import com.payrollsevice.payroll_service.processing.dto.PayrollDtos;
import com.payrollsevice.payroll_service.processing.service.PayrollService;
import com.payrollsevice.payroll_service.processing.service.RegularPayrollCalculator;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payroll")
@RequiredArgsConstructor
public class PayrollController {
    private final PayrollService payrollService;
    private final RegularPayrollCalculator calculator;
    private final PayslipDocumentDispatcher documents;

    @PostMapping
    public ResponseEntity<PayrollDtos.Response> create(@Valid @RequestBody PayrollDtos.CreateRequest request) {
        PayrollDtos.Response payroll = payrollService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(payroll.id())
            .toUri();

        return ResponseEntity.created(location).body(payroll);
    }

    @GetMapping
    public PagedModel<PayrollDtos.Response> list(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(payrollService.list(pageable));
    }

    @PostMapping("/{id}/calculation-preview")
    public List<PayslipDtos.Preview> preview(@PathVariable("id") UUID id,
                                           @Valid @RequestBody PayrollDtos.CalculationRequest request) {
        return calculator.preview(id, request);
    }

    @PostMapping("/{id}/calculate")
    public List<PayslipDtos.Response> calculate(
        @PathVariable("id") UUID id, @Valid @RequestBody PayrollDtos.CalculationRequest request
    ) {
        return calculator.calculate(id, request);
    }

    @PostMapping("/{id}/close")
    public PayrollDtos.Response close(@PathVariable("id") UUID id) {
        return payrollService.close(id);
    }

    @PostMapping("/{id}/payslip-documents")
    public ResponseEntity<Void> enqueuePayslipDocuments(@PathVariable("id") UUID id) {
        documents.enqueue(id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/finance-report")
    public ResponseEntity<PayrollDtos.FinanceReport> findFinanceReport(@PathVariable("id") UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(payrollService.findFinanceReport(id));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<PayrollDtos.SummaryResponse> findSummary(@PathVariable("id") UUID id) {
        return payrollService.findSummary(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.accepted()
                .header(HttpHeaders.RETRY_AFTER, "5")
                .cacheControl(CacheControl.noStore())
                .build());
    }

    @GetMapping("/{id}")
    public PayrollDtos.Response findById(@PathVariable("id") UUID id) {
        return payrollService.findById(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        payrollService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
