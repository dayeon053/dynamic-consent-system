package com.consentradar.consentradar.riskhistory;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.RiskScore;
import com.consentradar.consentradar.entity.User;
import com.consentradar.consentradar.repository.RiskScoreRepository;
import com.dynamicconsent.model.RiskResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 사용자별 개인 맞춤 대표 위험도를 날짜 단위로 append-only 저장/조회한다.
 * 같은 (user, company, 오늘 날짜) 조합의 대표(isRepresentative=true) row가 이미 있으면
 * 새로 저장하지 않고 건너뛴다 — 기존 row를 덮어쓰지 않으므로 날짜별 히스토리가 보존된다.
 * 배치 파이프라인(RiskPipelineService, user=null)과는 완전히 별개의 저장 경로다.
 *
 * TODO(Sprint03 2-4): 다연님의 개인 맞춤 위험도 계산 로직
 * (ConsentApiService.calculatePersonalRisk, 현재 PR #18 / feature/stage3-integration
 * 브랜치)과 아직 연결되지 않았다. 그 로직이 언제 트리거되는지(사용자가 동의를 토글할
 * 때마다 즉시 vs 별도 스케줄러로 일 1회)가 다연님과 아직 확인 전이라, saveIfAbsent()를
 * 어디서 호출할지는 확정되는 대로 별도로 연결한다.
 */
@Service
public class PersonalRiskHistoryService {

    private final RiskScoreRepository riskScoreRepository;

    public PersonalRiskHistoryService(RiskScoreRepository riskScoreRepository) {
        this.riskScoreRepository = riskScoreRepository;
    }

    @Transactional
    public Optional<RiskScore> saveIfAbsent(User user, Company company, RiskResult result) {
        LocalDate today = LocalDate.now();
        boolean alreadySaved = riskScoreRepository
                .existsByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                        user.getUserId(), company.getCompanyId(), today);

        if (alreadySaved) {
            return Optional.empty();
        }

        RiskScore riskScore = new RiskScore();
        riskScore.setUser(user);
        riskScore.setCompany(company);
        riskScore.setTotalScore(BigDecimal.valueOf(result.getScore()));
        riskScore.setGrade(RiskScore.Grade.valueOf(result.getGrade().name()));
        riskScore.setScoredAt(today);
        riskScore.setRepresentative(true);

        return Optional.of(riskScoreRepository.save(riskScore));
    }

    @Transactional(readOnly = true)
    public List<RiskScoreHistoryItemDto> getHistory(Long userId, Long companyId) {
        return riskScoreRepository
                .findByUser_UserIdAndCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtAsc(userId, companyId)
                .stream()
                .map(RiskScoreHistoryItemDto::from)
                .toList();
    }
}
