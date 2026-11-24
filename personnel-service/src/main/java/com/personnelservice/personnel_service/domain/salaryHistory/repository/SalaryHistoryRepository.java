package com.personnelservice.personnel_service.domain.salaryHistory.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.personnelservice.personnel_service.domain.salaryHistory.model.SalaryHistory;

public interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, UUID>, JpaSpecificationExecutor<SalaryHistory> {
    List<SalaryHistory> findByContractId(UUID contractId);

    default List<SalaryHistory> findOverlappingPeriod(UUID companyId, LocalDate start, LocalDate end) {
        return findAll((root, query, criteria) -> {
            var endDate = root.<LocalDate>get("effectiveUntil");
            return criteria.and(
                criteria.equal(root.get("contract").get("company").get("id"), companyId),
                criteria.lessThanOrEqualTo(root.<LocalDate>get("effectiveFrom"), end),
                criteria.or(criteria.isNull(endDate), criteria.greaterThanOrEqualTo(endDate, start))
            );
        }, Sort.by("effectiveFrom", "id"));
    }
}
