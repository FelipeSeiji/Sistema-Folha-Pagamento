package com.payrollsevice.payroll_service.attendanceSummary.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.payrollsevice.payroll_service.attendanceSummary.model.AttendanceSummary;

public interface AttendanceSummaryRepository extends JpaRepository<AttendanceSummary, UUID> {
    List<AttendanceSummary> findByPeriodAndContractIdIn(LocalDate period, List<UUID> contractIds);

    Optional<AttendanceSummary> findByContractIdAndPeriod(UUID contractId, LocalDate period);

    Page<AttendanceSummary> findByCompanyIdIn(Collection<UUID> companyIds, Pageable pageable);

    @Query("select distinct a.contractId from AttendanceSummary a where a.companyId is null")
    List<UUID> findContractsWithoutCompany();

    @Transactional
    @Modifying
    @Query("""
        update AttendanceSummary a set a.companyId = :companyId
        where a.contractId = :contractId and a.companyId is null
        """)
    void assignMissingCompany(@Param("contractId") UUID contractId, @Param("companyId") UUID companyId);
}
