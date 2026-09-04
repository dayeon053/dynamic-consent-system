package com.consentradar.consentradar.scheduler;

import com.consentradar.consentradar.api.PersonalRiskCalculator;
import com.consentradar.consentradar.crawler.PolicyBodyCrawler;
import com.consentradar.consentradar.crawler.PolicyChangeDetectionService;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.entity.User;
import com.consentradar.consentradar.pipeline.RiskPipelineService;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import com.consentradar.consentradar.repository.UserConsentCheckRepository;
import com.consentradar.consentradar.riskhistory.PersonalRiskHistoryService;
import com.dynamicconsent.model.RiskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 기업 1건에 대한 크롤링 → 변경감지 → (조건부) 위험도 재산출을 하나의 트랜잭션으로 묶어 실행한다.
 *
 * [트랜잭션 경계 통합 — 2026-07-30, docs/known_issues.md "PolicySnapshot 저장과 위험도
 * 재산출의 트랜잭션 경계 불일치" 해결] 이전에는 {@link PolicyChangeDetectionService#detectAndSave}와
 * {@link RiskPipelineService#analyzeAndSaveRisk}가 각각 별도 {@code @Transactional}이라,
 * 위험도 재산출이 실패해도(예: LLM 재시도 소진) 이미 커밋된 스냅샷은 되돌릴 수 없었다. 이제
 * {@link #processCompany}가 두 호출을 하나의 트랜잭션으로 묶어, 실패 시 스냅샷 저장까지 함께
 * 롤백되도록 한다.
 *
 * {@link PolicyCrawlScheduler}에 직접 두지 않고 별도 빈으로 분리한 이유: Spring의 프록시 기반
 * {@code @Transactional}은 같은 빈 안에서의 자기호출(self-invocation)을 가로채지 못한다 —
 * {@code PolicyCrawlScheduler.runForCompany()}/{@code runPipeline()}이 내부에서
 * {@code this.processCompany()}를 호출하는 구조 그대로 이 메서드에 애노테이션만 붙이면 조용히
 * 아무 효과가 없다. 이 프로젝트는 이미 같은 이유로 트랜잭션 단위를 별도 빈으로 분리해
 * 오케스트레이터(컨트롤러/스케줄러)가 주입받아 호출하는 패턴을 쓰고 있어(예:
 * {@code ConsentApiController} → {@code ConsentApiService}), 여기서도 같은 패턴을 따른다.
 */
@Component
public class PolicyCrawlProcessor {

    private static final Logger log = LoggerFactory.getLogger(PolicyCrawlProcessor.class);

    private final PolicyBodyCrawler policyBodyCrawler;
    private final PolicyChangeDetectionService policyChangeDetectionService;
    private final PolicySnapshotRepository policySnapshotRepository;
    private final RiskPipelineService riskPipelineService;
    private final UserConsentCheckRepository userConsentCheckRepository;
    private final PersonalRiskCalculator personalRiskCalculator;
    private final PersonalRiskHistoryService personalRiskHistoryService;

    public PolicyCrawlProcessor(PolicyBodyCrawler policyBodyCrawler,
                                 PolicyChangeDetectionService policyChangeDetectionService,
                                 PolicySnapshotRepository policySnapshotRepository,
                                 RiskPipelineService riskPipelineService,
                                 UserConsentCheckRepository userConsentCheckRepository,
                                 PersonalRiskCalculator personalRiskCalculator,
                                 PersonalRiskHistoryService personalRiskHistoryService) {
        this.policyBodyCrawler = policyBodyCrawler;
        this.policyChangeDetectionService = policyChangeDetectionService;
        this.policySnapshotRepository = policySnapshotRepository;
        this.riskPipelineService = riskPipelineService;
        this.userConsentCheckRepository = userConsentCheckRepository;
        this.personalRiskCalculator = personalRiskCalculator;
        this.personalRiskHistoryService = personalRiskHistoryService;
    }

    /**
     * 크롤링 → 변경감지 → (최초 수집이거나 변경 있으면) 위험도 재산출을 하나의 트랜잭션 안에서
     * 수행한다. 위험도 재산출이 실패하면 이 트랜잭션 전체가 롤백되어 스냅샷 저장도 함께
     * 되돌아간다 — "스냅샷은 최신인데 위험도는 비어있는" 상태가 남지 않는다.
     *
     * 트레이드오프: 크롤링(느림)과 LLM 호출(더 느림)이 한 트랜잭션 안에 들어가 DB 커넥션을
     * 그 시간만큼 점유한다. 현재 규모(5개 기업, 새벽 배치 순차 처리)에서는 위험이 낮다고
     * 판단했지만, 관리자 수동 트리거(`POST /admin/crawl/{id}`)가 배치와 동시에 여러 건
     * 겹치는 경우 HikariCP 커넥션 풀(기본 10개)이 소진될 이론적 가능성은 남아있다 — 기업 수가
     * 늘어나면 재검토 필요(docs/known_issues.md 참고).
     *
     * [개인 맞춤 위험도 히스토리 배치 연결 — 2026-08-08]
     * 정책 변경 여부(shouldAnalyze)와 무관하게 매일 밤 이 기업에 {@code UserConsentCheck}
     * 이력이 있는(=이 기업을 실제로 접한) 사용자 전원에 대해 개인 맞춤 위험도를 계산해
     * {@link PersonalRiskHistoryService#saveIfAbsent}로 저장한다 — 정책이 그대로여도 매일의
     * 스냅샷이 쌓여야 위험도 추이 그래프(2-4 API)가 끊기지 않는다.
     */
    @Transactional
    public CompanyCrawlResult processCompany(Company company) {
        return processCompany(company, false);
    }

    /**
     * force=true면 shouldAnalyze 판단(isFirstCollection || changed)을 건너뛰고 무조건
     * analyzeAndSaveRisk()를 실행한다. 재크롤링 텍스트가 동일해도 예전 오염된 ConsentItem을
     * 정리해야 하는 관리 목적으로 사용한다 — 예: LLM_ENABLED=false였던 시절 mock으로 만들어진
     * ConsentItem이 실제 페이지 내용은 그대로라 재크롤링해도 changed=false로 스킵되면서
     * 영영 정리되지 않는 경우(2026-08-26 네이버 사례), ConsentItemUpsertService.
     * deactivateMissing()이 이번 크롤링 결과 기준으로 예전 항목을 소프트 삭제하도록 강제로
     * 한 번 더 돌려야 한다. 관리자 수동 트리거(POST /admin/crawl/{id}?force=true) 전용이며
     * 배치({@link PolicyCrawlScheduler#runPipeline()})는 이 오버로드를 쓰지 않는다.
     */
    @Transactional
    public CompanyCrawlResult processCompany(Company company, boolean force) {
        boolean isFirstCollection = policySnapshotRepository
                .findFirstByCompany_CompanyIdOrderByCrawledAtDesc(company.getCompanyId())
                .isEmpty();

        String rawText = policyBodyCrawler.fetchCleanText(company.getPrivacyUrl());
        PolicySnapshot snapshot = policyChangeDetectionService.detectAndSave(company, rawText);

        boolean shouldAnalyze = force || isFirstCollection || snapshot.isChanged();
        if (shouldAnalyze) {
            riskPipelineService.analyzeAndSaveRisk(company, rawText);
        }

        saveTodaysPersonalRiskHistoryForInterestedUsers(company);

        return new CompanyCrawlResult(company.getCompanyId(), company.getCompanyName(),
                snapshot.isChanged(), shouldAnalyze);
    }

    /**
     * 이 기업에 {@code UserConsentCheck} row가 하나라도 있는 사용자(=이 기업의 동의 화면을
     * 실제로 열어본 적 있는 사용자) 전원에 대해 오늘자 개인 맞춤 위험도 히스토리를 저장한다.
     * 사용자 한 명의 동의 항목 데이터가 잘못돼 있어도({@link PersonalRiskCalculator}가 던지는
     * {@link IllegalArgumentException}) 그 사용자만 건너뛰고 나머지 사용자·이 기업의 크롤링/
     * 위험도 재산출 자체는 영향받지 않아야 한다 —
     * {@link com.consentradar.consentradar.api.ConsentApiService#safeCalculate}와 동일한
     * 방어 패턴이다.
     */
    private void saveTodaysPersonalRiskHistoryForInterestedUsers(Company company) {
        List<User> interestedUsers = userConsentCheckRepository
                .findDistinctUsersByConsentItem_Company_CompanyId(company.getCompanyId());

        for (User user : interestedUsers) {
            try {
                RiskResult result = personalRiskCalculator.calculate(user.getUserId(), company.getCompanyId());
                if (result != null) {
                    personalRiskHistoryService.saveIfAbsent(user, company, result);
                }
            } catch (IllegalArgumentException e) {
                log.warn("userId={}, companyId={} 개인 맞춤 위험도 히스토리 저장 실패 — 잘못된 동의 항목 데이터로 추정, 건너뜀: {}",
                        user.getUserId(), company.getCompanyId(), e.getMessage());
            }
        }
    }
}
