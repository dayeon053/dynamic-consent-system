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
 * 배치 파이프라인(RiskPipelineService, user=null)과는 완전히 별개의 저장 경로다.
 *
 * 저장 호출부는 두 곳이다 (2026-08-08, 배치 연결 확정):
 * - {@link com.consentradar.consentradar.scheduler.PolicyCrawlProcessor} — 매일 밤 배치가
 *   기업별로 {@code UserConsentCheck}가 있는(=이 기업을 실제로 접한) 사용자 전원에 대해
 *   {@link #saveIfAbsent}를 호출한다. 오늘자 row가 이미 있으면 건너뛴다.
 * - {@link com.consentradar.consentradar.api.ConsentApiService#toggleConsent} — 사용자가
 *   PATCH로 동의를 토글할 때마다 {@link #saveOrUpdateToday}를 호출한다. 오늘자 row가 있으면
 *   최신 점수로 갱신하고, 없으면 새로 만든다 — 같은 날 여러 번 토글해도 오늘자 row는 1개만
 *   유지하되, 날짜가 바뀌면 어제 row는 건드리지 않고 새 row가 생긴다(append-only).
 *
 * [해결됨 — 2026-08-08] 과거 {@code ConsentApiService.toggleConsent()}는 "가장 최근
 * 대표 row"를 날짜 무관하게 찾아 덮어쓰고 그 row의 날짜까지 오늘로 바꿔치기해서, 사실상
 * 히스토리가 전혀 쌓이지 않고 있었다(자세한 내용은 docs/known_issues.md 참고). 지금은
 * 두 호출부 모두 "오늘자 row"로 명시적으로 범위를 좁혀 조회하므로 과거 날짜 row가
 * 보존된다.
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

    /**
     * PATCH 토글 경로 전용: 오늘자 대표 row가 있으면 최신 점수로 갱신하고, 없으면 새로
     * 만든다. {@link #saveIfAbsent}와 달리 오늘자 row가 이미 있어도 조용히 건너뛰지 않고
     * 항상 최신 계산 결과를 반영한다 — 사용자가 같은 날 토글을 여러 번 하면 그때마다
     * 화면에 최신 위험도가 보여야 하기 때문이다. 과거 날짜 row는 조회 조건에 오늘 날짜가
     * 포함돼 있어 애초에 대상이 되지 않는다(append-only 보존).
     *
     * {@code saveAndFlush}로 저장한다 — {@link RiskScore}의 유니크 제약
     * (user_id, company_id, scored_at, is_representative)/낙관적 락(version) 위반을 호출부
     * ({@link com.consentradar.consentradar.api.ConcurrentUpdateRetrier}가 새 트랜잭션으로
     * 재시도)가 즉시 감지할 수 있도록 한다.
     */
    @Transactional
    public RiskScore saveOrUpdateToday(User user, Company company, RiskResult result) {
        LocalDate today = LocalDate.now();
        RiskScore riskScore = riskScoreRepository
                .findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                        user.getUserId(), company.getCompanyId(), today)
                .orElseGet(() -> {
                    RiskScore r = new RiskScore();
                    r.setUser(user);
                    r.setCompany(company);
                    r.setScoredAt(today);
                    r.setRepresentative(true);
                    return r;
                });

        riskScore.setTotalScore(BigDecimal.valueOf(result.getScore()));
        riskScore.setGrade(RiskScore.Grade.valueOf(result.getGrade().name()));

        return riskScoreRepository.saveAndFlush(riskScore);
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
