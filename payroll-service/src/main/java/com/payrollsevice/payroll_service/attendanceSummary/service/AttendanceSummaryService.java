package com.payrollsevice.payroll_service.attendanceSummary.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.payrollsevice.payroll_service.attendanceSummary.dto.AttendanceSummaryDtos;
import com.payrollsevice.payroll_service.attendanceSummary.model.AttendanceSummary;
import com.payrollsevice.payroll_service.attendanceSummary.repository.AttendanceSummaryRepository;

import com.payrollsevice.payroll_service.personnel.PersonnelClient;
import com.payrollsevice.payroll_service.personnel.PersonnelUnavailableException;
import com.payrollsevice.payroll_service.security.PayrollCompanyAccess;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttendanceSummaryService {
    private final AttendanceSummaryRepository repository;
    private final PersonnelClient personnel;


    public AttendanceSummaryDtos.Response create(AttendanceSummaryDtos.Request request, Jwt principal) {
        UUID companyId;
        try {
            companyId = personnel.findContractCompanyId(request.contractId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employment contract not found."));
        } catch (PersonnelUnavailableException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Contract company is unavailable.", exception);
        }
        PayrollCompanyAccess.require(principal, companyId);
        AttendanceSummary entity = new AttendanceSummary();
        entity.setContractId(request.contractId());
        entity.setCompanyId(companyId);
        entity.setPeriod(request.period().atDay(1));
        applyChanges(entity, request);
        return AttendanceSummaryDtos.Response.from(repository.saveAndFlush(entity));
    }

    @Transactional
    public AttendanceSummaryDtos.Response update(UUID id, AttendanceSummaryDtos.Request request, Jwt principal) {
        AttendanceSummary entity = requireCompanySummary(id, principal);
        if (!entity.getContractId().equals(request.contractId())
                || !entity.getPeriod().equals(request.period().atDay(1))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attendance contract and period cannot be changed.");
        }
        applyChanges(entity, request);
        repository.flush();
        return AttendanceSummaryDtos.Response.from(entity);
    }

    @Transactional
    public void delete(UUID id, Jwt principal) {
        repository.delete(requireCompanySummary(id, principal));
    }

    @Transactional(readOnly = true)
    public Page<AttendanceSummaryDtos.Response> list(Pageable pageable, Jwt principal) {
        var companyIds = PayrollCompanyAccess.companyIds(principal);
        if (companyIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return repository.findByCompanyIdIn(companyIds, pageable).map(AttendanceSummaryDtos.Response::from);
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryDtos.Response findById(UUID id, Jwt principal) {
        return AttendanceSummaryDtos.Response.from(requireCompanySummary(id, principal));
    }

    private AttendanceSummary requireCompanySummary(UUID id, Jwt principal) {
        var summary = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance summary not found."));
        PayrollCompanyAccess.require(principal, summary.getCompanyId());
        return summary;
    }

    private static void applyChanges(AttendanceSummary entity, AttendanceSummaryDtos.Request request) {
        entity.setWorkedMinutes(request.workedMinutes());
        entity.setOvertimeMinutes(request.overtimeMinutes());
        entity.setAbsenceMinutes(request.absenceMinutes());
    }
}
