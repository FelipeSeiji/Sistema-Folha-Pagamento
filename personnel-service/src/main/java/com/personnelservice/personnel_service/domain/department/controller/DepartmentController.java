package com.personnelservice.personnel_service.domain.department.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.personnelservice.personnel_service.domain.department.dto.DepartmentDtos;
import com.personnelservice.personnel_service.domain.department.service.DepartmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/department")
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentService departmentService;

    @PostMapping
    public ResponseEntity<DepartmentDtos.Response> create(@Valid @RequestBody DepartmentDtos.Request request) {
        DepartmentDtos.Response department = departmentService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(department.id())
            .toUri();

        return ResponseEntity.created(location).body(department);
    }

    @GetMapping
    public PagedModel<DepartmentDtos.Response> list(
        @RequestParam(name = "companyId", required = false) UUID companyId,
        @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return new PagedModel<>(departmentService.list(companyId, pageable));
    }

    @PutMapping("/{id}")
    public DepartmentDtos.Response update(
        @PathVariable("id") UUID id,
        @Valid @RequestBody DepartmentDtos.UpdateRequest request
    ) {
        return departmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable("id") UUID id) {
        departmentService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public DepartmentDtos.Response findById(@PathVariable("id") UUID id) {
        return departmentService.findById(id);
    }
}
