package com.personnelservice.personnel_service.domain.employee.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.personnelservice.personnel_service.domain.employee.dto.EmployeeDtos;
import com.personnelservice.personnel_service.domain.employee.service.EmployeeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/employee")
@RequiredArgsConstructor
public class EmployeeController {
    private final EmployeeService employeeService;

    @PostMapping
    public ResponseEntity<EmployeeDtos.Response> create(@Valid @RequestBody EmployeeDtos.Request request) {
        EmployeeDtos.Response employee = employeeService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(employee.id())
            .toUri();

        return ResponseEntity.created(location).body(employee);
    }

    @GetMapping
    public PagedModel<EmployeeDtos.Response> list(
        @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return new PagedModel<>(employeeService.list(pageable));
    }

    @GetMapping("/{id}")
    public EmployeeDtos.Response findById(@PathVariable("id") UUID id) {
        return employeeService.findById(id);
    }
}
