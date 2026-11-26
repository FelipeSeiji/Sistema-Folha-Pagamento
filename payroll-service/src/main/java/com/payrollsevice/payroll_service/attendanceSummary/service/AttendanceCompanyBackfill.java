package com.payrollsevice.payroll_service.attendanceSummary.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.payrollsevice.payroll_service.attendanceSummary.repository.AttendanceSummaryRepository;
import com.payrollsevice.payroll_service.personnel.PersonnelClient;
import com.payrollsevice.payroll_service.personnel.PersonnelUnavailableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "payroll.attendance.company-backfill.enabled", havingValue = "true")
public class AttendanceCompanyBackfill implements ApplicationRunner {
    private final AttendanceSummaryRepository repository;
    private final PersonnelClient personnel;

    @Override
    public void run(ApplicationArguments args) {
        for (var contractId : repository.findContractsWithoutCompany()) {
            try {
                personnel.findContractCompanyId(contractId).ifPresentOrElse(
                    companyId -> repository.assignMissingCompany(contractId, companyId),
                    () -> log.warn("Attendance company backfill skipped missing contract {}", contractId));
            } catch (PersonnelUnavailableException exception) {
                log.warn("Attendance company backfill paused because personnel is unavailable. Rerun to resume.");
                return;
            }
        }
    }
}
