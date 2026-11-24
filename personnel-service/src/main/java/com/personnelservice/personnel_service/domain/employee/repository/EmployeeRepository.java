package com.personnelservice.personnel_service.domain.employee.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personnelservice.personnel_service.domain.employee.model.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
}
