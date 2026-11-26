package com.payrollsevice.payroll_service.attendanceSummary.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
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

import com.payrollsevice.payroll_service.attendanceSummary.dto.AttendanceSummaryDtos;
import com.payrollsevice.payroll_service.attendanceSummary.service.AttendanceSummaryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/attendance-summary")
@RequiredArgsConstructor
public class AttendanceSummaryController {
    private final AttendanceSummaryService service;

    @PostMapping
    public ResponseEntity<AttendanceSummaryDtos.Response> create(
        @Valid @RequestBody AttendanceSummaryDtos.Request request, @AuthenticationPrincipal Jwt principal
    ) {
        var created = service.create(request, principal);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PagedModel<AttendanceSummaryDtos.Response> list(
        @PageableDefault(size = 20, sort = "id") Pageable pageable, @AuthenticationPrincipal Jwt principal
    ) {
        return new PagedModel<>(service.list(pageable, principal));
    }

    @PutMapping("/{id}")
    public AttendanceSummaryDtos.Response update(
        @PathVariable("id") UUID id, @Valid @RequestBody AttendanceSummaryDtos.Request request,
        @AuthenticationPrincipal Jwt principal
    ) {
        return service.update(id, request, principal);
    }

    @GetMapping("/{id}")
    public AttendanceSummaryDtos.Response findById(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt principal) {
        return service.findById(id, principal);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt principal) {
        service.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
