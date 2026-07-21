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

    /** 사용자+기업의 개인 맞춤 대표 위험도 날짜별 히스토리 (오래된 순). */
    List<RiskScore> findByUser_UserIdAndCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtAsc(
            Long userId, Long companyId);

    /** 배치 파이프라인이 저장하는 기업 대표(전체 항목 기준, user=null) 위험도. 개인 맞춤 용도로 쓰지 말 것. */
    Optional<RiskScore> findTopByCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtDesc(Long companyId);

    /** 특정 사용자의 개인 맞춤 대표 위험도 (company + user 로 구분되는 별도 row). */
    Optional<RiskScore> findTopByCompany_CompanyIdAndUser_UserIdAndIsRepresentativeTrueOrderByScoredAtDesc(
            Long companyId, Long userId);
}
