package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.PolicySnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicySnapshotRepository extends JpaRepository<PolicySnapshot, Long> {

    Optional<PolicySnapshot> findFirstByCompany_CompanyIdOrderByCrawledAtDesc(Long companyId);

    /** 해당 기업에 저장된 PolicySnapshot이 하나라도 있는지 (company 삭제 가능 여부 판단용). */
    boolean existsByCompany_CompanyId(Long companyId);

    List<PolicySnapshot> findByCompany_CompanyId(Long companyId);

    /** GET /notices — 전체 기업의 스냅샷을 확인 시각(crawledAt) 내림차순으로 페이징 조회. */
    Page<PolicySnapshot> findAllByOrderByCrawledAtDesc(Pageable pageable);
}