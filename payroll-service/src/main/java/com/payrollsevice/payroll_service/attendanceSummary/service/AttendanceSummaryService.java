package com.payrollsevice.payroll_service.attendanceSummary.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.payrollsevice.payroll_service.attendanceSummary.dto.AttendanceSummaryDtos;
import com.payrollsevice.payroll_service.attendanceSummary.model.AttendanceSummary;
import com.payrollsevice.payroll_service.attendanceSummary.repository.AttendanceSummaryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttendanceSummaryService {
    private final AttendanceSummaryRepository repository;


    @Transactional
    public AttendanceSummaryDtos.Response create(AttendanceSummaryDtos.Request request) {
        AttendanceSummary entity = new AttendanceSummary();
        entity.setContractId(request.contractId());
        entity.setPeriod(request.period().atDay(1));
        entity.setWorkedMinutes(request.workedMinutes());
        entity.setOvertimeMinutes(request.overtimeMinutes());
        entity.setAbsenceMinutes(request.absenceMinutes());
        return AttendanceSummaryDtos.Response.from(repository.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public Page<AttendanceSummaryDtos.Response> list(Pageable pageable) {
        return repository.findAll(pageable).map(AttendanceSummaryDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryDtos.Response findById(UUID id) {
        return repository.findById(id)
            .map(AttendanceSummaryDtos.Response::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance summary not found."));
    }
}
