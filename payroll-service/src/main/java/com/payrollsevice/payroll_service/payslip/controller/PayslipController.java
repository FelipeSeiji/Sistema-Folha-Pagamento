package com.payrollsevice.payroll_service.payslip.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.payrollsevice.payroll_service.payslip.dto.PayslipDtos;
import com.payrollsevice.payroll_service.payslip.service.PayslipService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payslip")
@RequiredArgsConstructor
public class PayslipController {
    private final PayslipService service;

    @PostMapping
    public ResponseEntity<PayslipDtos.Response> create(
        @Valid @RequestBody PayslipDtos.Request request, @AuthenticationPrincipal Jwt principal
    ) {
        var created = service.create(request, principal);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PagedModel<PayslipDtos.Response> list(
        @PageableDefault(size = 20, sort = "id") Pageable pageable, @AuthenticationPrincipal Jwt principal
    ) {
        return new PagedModel<>(service.list(pageable, principal));
    }

    @PutMapping("/{id}")
    public PayslipDtos.Response update(
        @PathVariable("id") UUID id, @Valid @RequestBody PayslipDtos.Request request,
        @AuthenticationPrincipal Jwt principal
    ) {
        return service.update(id, request, principal);
    }

    @GetMapping("/{id}")
    public PayslipDtos.Response findById(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt principal) {
        return service.findById(id, principal);
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<?> downloadDocument(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt principal) {
        var document = service.findDocument(id, principal);
        if (document.isEmpty()) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The payslip PDF is not available yet.");
            return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).body(problem);
        }

        byte[] pdf = document.orElseThrow();
        var attachment = ContentDisposition.attachment().filename("payslip-" + id + ".pdf").build();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, attachment.toString())
            .cacheControl(CacheControl.noStore())
            .body(pdf);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt principal) {
        service.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
