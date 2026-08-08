package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {

    /** 오늘 이미 개인 맞춤 대표 위험도가 저장됐는지 확인 (append-only dedup 용도). */
    boolean existsByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
            Long userId, Long companyId, LocalDate scoredAt);

    /** (user, company, 날짜) 조합의 대표 row 개수. 동시 토글 경합 시 중복 insert 여부 검증용. */
    long countByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
            Long userId, Long companyId, LocalDate scoredAt);

    /** 사용자+기업의 개인 맞춤 대표 위험도 날짜별 히스토리 (오래된 순). */
    List<RiskScore> findByUser_UserIdAndCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtAsc(
            Long userId, Long companyId);

    List<RiskScore> findByCompany_CompanyId(Long companyId);

    /** 배치 파이프라인이 저장하는 기업 대표(전체 항목 기준, user=null) 위험도. 개인 맞춤 용도로 쓰지 말 것. */
    Optional<RiskScore> findTopByCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtDesc(Long companyId);

    /**
     * 특정 사용자+기업의 "오늘자" 개인 맞춤 대표 위험도 row. PATCH 토글/배치 저장 양쪽이
     * 이 조회로 오늘 이미 저장된 row가 있는지 확인해 upsert(있으면 갱신, 없으면 신규)한다 —
     * append-only(날짜별 row 보존)를 위해 날짜로 반드시 범위를 좁혀야 한다(과거 임의의
     * 최근 row를 가져와 덮어쓰면 히스토리가 소실된다).
     */
    Optional<RiskScore> findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
            Long userId, Long companyId, LocalDate scoredAt);

    /** 해당 기업에 저장된 RiskScore가 하나라도 있는지 (company 삭제 가능 여부 판단용). */
    boolean existsByCompany_CompanyId(Long companyId);
}
