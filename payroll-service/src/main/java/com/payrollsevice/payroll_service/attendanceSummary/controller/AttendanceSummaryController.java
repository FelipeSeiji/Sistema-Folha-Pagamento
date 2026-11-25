package com.payrollsevice.payroll_service.attendanceSummary.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<AttendanceSummaryDtos.Response> create(@Valid @RequestBody AttendanceSummaryDtos.Request request) {
        var created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PagedModel<AttendanceSummaryDtos.Response> list(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(service.list(pageable));
    }

    @PutMapping("/{id}")
    public AttendanceSummaryDtos.Response update(@PathVariable("id") UUID id, @Valid @RequestBody AttendanceSummaryDtos.Request request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}")
    public AttendanceSummaryDtos.Response findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }
}
