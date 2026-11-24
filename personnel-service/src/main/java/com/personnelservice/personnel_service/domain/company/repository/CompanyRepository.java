package com.personnelservice.personnel_service.domain.company.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personnelservice.personnel_service.domain.company.model.Company;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    boolean existsByCnpj(String cnpj);
}
