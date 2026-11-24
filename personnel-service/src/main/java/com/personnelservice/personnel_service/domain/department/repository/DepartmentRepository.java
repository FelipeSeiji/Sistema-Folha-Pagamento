package com.personnelservice.personnel_service.domain.department.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.personnelservice.personnel_service.domain.department.model.Department;

import jakarta.persistence.LockModeType;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    Page<Department> findByCompanyIdAndActiveTrue(UUID companyId, Pageable pageable);
    Page<Department> findByActiveTrue(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Department> findLockedById(UUID id);
}
