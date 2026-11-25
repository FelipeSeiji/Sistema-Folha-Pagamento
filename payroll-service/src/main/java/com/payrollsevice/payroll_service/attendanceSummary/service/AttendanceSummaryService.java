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
        applyChanges(entity, request);
        return AttendanceSummaryDtos.Response.from(repository.saveAndFlush(entity));
    }

    @Transactional
    public AttendanceSummaryDtos.Response update(UUID id, AttendanceSummaryDtos.Request request) {
        AttendanceSummary entity = requireAttendanceSummary(id);
        if (!entity.getContractId().equals(request.contractId())
                || !entity.getPeriod().equals(request.period().atDay(1))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attendance contract and period cannot be changed.");
        }
        applyChanges(entity, request);
        repository.flush();
        return AttendanceSummaryDtos.Response.from(entity);
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(requireAttendanceSummary(id));
    }

    @Transactional(readOnly = true)
    public Page<AttendanceSummaryDtos.Response> list(Pageable pageable) {
        return repository.findAll(pageable).map(AttendanceSummaryDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryDtos.Response findById(UUID id) {
        return AttendanceSummaryDtos.Response.from(requireAttendanceSummary(id));
    }

    private AttendanceSummary requireAttendanceSummary(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance summary not found."));
    }

    private static void applyChanges(AttendanceSummary entity, AttendanceSummaryDtos.Request request) {
        entity.setWorkedMinutes(request.workedMinutes());
        entity.setOvertimeMinutes(request.overtimeMinutes());
        entity.setAbsenceMinutes(request.absenceMinutes());
    }
}
