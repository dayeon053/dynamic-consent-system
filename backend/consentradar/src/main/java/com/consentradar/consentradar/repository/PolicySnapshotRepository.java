package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.PolicySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicySnapshotRepository extends JpaRepository<PolicySnapshot, Long> {

    Optional<PolicySnapshot> findFirstByCompany_CompanyIdOrderByCrawledAtDesc(Long companyId);

    /** 해당 기업에 저장된 PolicySnapshot이 하나라도 있는지 (company 삭제 가능 여부 판단용). */
    boolean existsByCompany_CompanyId(Long companyId);
}