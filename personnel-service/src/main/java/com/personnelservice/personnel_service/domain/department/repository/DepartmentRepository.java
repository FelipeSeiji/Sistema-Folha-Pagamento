package com.personnelservice.personnel_service.domain.department.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.personnelservice.personnel_service.domain.department.model.Department;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    Page<Department> findByCompanyId(UUID companyId, Pageable pageable);
}
