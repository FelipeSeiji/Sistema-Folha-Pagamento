package com.personnelservice.personnel_service.domain.company.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.personnelservice.personnel_service.domain.company.model.Company;

import jakarta.persistence.LockModeType;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    boolean existsByCnpj(String cnpj);
    Page<Company> findByActiveTrue(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Company> findLockedById(UUID id);
}
