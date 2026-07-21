package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {

    /** 오늘 이미 개인 맞춤 대표 위험도가 저장됐는지 확인 (append-only dedup 용도). */
    boolean existsByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
            Long userId, Long companyId, LocalDate scoredAt);

    /** 사용자+기업의 개인 맞춤 대표 위험도 날짜별 히스토리 (오래된 순). */
    List<RiskScore> findByUser_UserIdAndCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtAsc(
            Long userId, Long companyId);
}
