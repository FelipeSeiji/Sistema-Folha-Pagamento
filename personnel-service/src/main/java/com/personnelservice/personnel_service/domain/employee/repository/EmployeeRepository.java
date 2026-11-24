package com.personnelservice.personnel_service.domain.employee.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.personnelservice.personnel_service.domain.employee.model.Employee;

import jakarta.persistence.LockModeType;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Page<Employee> findByActiveTrue(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Employee> findLockedById(UUID id);
}
