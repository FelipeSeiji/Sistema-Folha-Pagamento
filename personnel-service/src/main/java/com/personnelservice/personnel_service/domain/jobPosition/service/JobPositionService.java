package com.personnelservice.personnel_service.domain.jobPosition.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import com.personnelservice.personnel_service.domain.jobPosition.dto.JobPositionDtos;
import com.personnelservice.personnel_service.domain.jobPosition.model.JobPosition;
import com.personnelservice.personnel_service.domain.jobPosition.repository.JobPositionRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class JobPositionService {
    private final JobPositionRepository jobPositionRepository;

    @Transactional
    public JobPositionDtos.Response create(@NotNull @Valid JobPositionDtos.Request request) {
        JobPosition entity = new JobPosition(null, request.name(), request.description());
        return JobPositionDtos.Response.from(jobPositionRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<JobPositionDtos.Response> list(Pageable pageable) {
        return jobPositionRepository.findAll(pageable).map(JobPositionDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public JobPositionDtos.Response findById(UUID id) {
        return jobPositionRepository.findById(id)
            .map(JobPositionDtos.Response::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job position not found."));
    }
}
