package com.personnelservice.personnel_service.domain.jobPosition.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.personnelservice.personnel_service.domain.jobPosition.dto.JobPositionDtos;
import com.personnelservice.personnel_service.domain.jobPosition.service.JobPositionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/job-position")
@RequiredArgsConstructor
public class JobPositionController {
    private final JobPositionService jobPositionService;

    @PostMapping
    public ResponseEntity<JobPositionDtos.Response> create(@Valid @RequestBody JobPositionDtos.Request request) {
        JobPositionDtos.Response jobPosition = jobPositionService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(jobPosition.id())
            .toUri();

        return ResponseEntity.created(location).body(jobPosition);
    }

    @GetMapping("/{id}")
    public JobPositionDtos.Response findById(@PathVariable("id") UUID id) {
        return jobPositionService.findById(id);
    }
}
