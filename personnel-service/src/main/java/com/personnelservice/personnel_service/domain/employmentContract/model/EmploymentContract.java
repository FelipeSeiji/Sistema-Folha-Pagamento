package com.personnelservice.personnel_service.domain.employmentContract.model;

import java.time.LocalDate;
import java.util.UUID;

import com.personnelservice.personnel_service.domain.company.model.Company;
import com.personnelservice.personnel_service.domain.department.model.Department;
import com.personnelservice.personnel_service.domain.employee.model.Employee;
import com.personnelservice.personnel_service.domain.jobPosition.model.JobPosition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TB_EMPLOYMENT_CONTRACT")
@Getter
@Setter
@NoArgsConstructor
public class EmploymentContract {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_position_id")
    private JobPosition position;

    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;

    @Column(nullable = false)
    private LocalDate hireDate;

    private LocalDate terminationDate;

    @Column(nullable = false)
    private int monthlyWorkHours;
}
