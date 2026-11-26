package com.personnelservice.personnel_service.domain.employmentContract.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import com.personnelservice.personnel_service.domain.employmentContract.model.EmploymentContract;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, UUID>, JpaSpecificationExecutor<EmploymentContract> {
    Page<EmploymentContract> findByCompanyIdIn(Collection<UUID> companyIds, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmploymentContract> findLockedById(UUID id);

    default List<EmploymentContract> findOverlappingPeriod(UUID companyId, LocalDate start, LocalDate end) {
        return findAll((root, query, criteria) -> {
            root.fetch("employee");
            var endDate = root.<LocalDate>get("terminationDate");
            return criteria.and(
                criteria.equal(root.get("company").get("id"), companyId),
                criteria.lessThanOrEqualTo(root.<LocalDate>get("hireDate"), end),
                criteria.or(criteria.isNull(endDate), criteria.greaterThanOrEqualTo(endDate, start))
            );
        }, Sort.by("id"));
    }
}
