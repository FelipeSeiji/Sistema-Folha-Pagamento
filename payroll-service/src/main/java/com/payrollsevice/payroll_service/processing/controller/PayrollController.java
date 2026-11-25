package com.payrollsevice.payroll_service.processing.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.payrollsevice.payroll_service.processing.dto.PayrollDtos;
import com.payrollsevice.payroll_service.processing.service.PayrollService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payroll")
@RequiredArgsConstructor
public class PayrollController {
    private final PayrollService payrollService;

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
