package com.payrollsevice.payroll_service.rubric.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.payrollsevice.payroll_service.rubric.model.Rubric;

public interface RubricRepository extends JpaRepository<Rubric, UUID> {
    List<Rubric> findByCompanyId(UUID companyId);

    Page<Rubric> findByCompanyIdIn(Collection<UUID> companyIds, Pageable pageable);
}
