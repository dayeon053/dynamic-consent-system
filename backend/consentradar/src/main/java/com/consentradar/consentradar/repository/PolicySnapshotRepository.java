package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.PolicySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicySnapshotRepository extends JpaRepository<PolicySnapshot, Long> {

    Optional<PolicySnapshot> findFirstByCompany_CompanyIdOrderByCrawledAtDesc(Long companyId);
}