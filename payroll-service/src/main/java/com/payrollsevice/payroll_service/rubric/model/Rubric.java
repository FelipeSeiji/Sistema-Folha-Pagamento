package com.payrollsevice.payroll_service.rubric.model;

import java.util.UUID;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TB_RUBRIC")
@Getter
@Setter
@NoArgsConstructor
public class Rubric {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RubricType type;

    @Column(nullable = false)
    private boolean subjectToInss;

    @Column(nullable = false)
    private boolean subjectToIrrf;

    @Column(nullable = false)
    private boolean subjectToFgts;
}
