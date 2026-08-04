package com.consentradar.consentradar.scheduler;

import com.consentradar.consentradar.crawler.PolicyBodyCrawler;
import com.consentradar.consentradar.crawler.PolicyChangeDetectionService;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.pipeline.RiskPipelineService;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    private final PolicyBodyCrawler policyBodyCrawler;
    private final PolicyChangeDetectionService policyChangeDetectionService;
    private final PolicySnapshotRepository policySnapshotRepository;
    private final RiskPipelineService riskPipelineService;

    public PolicyCrawlProcessor(PolicyBodyCrawler policyBodyCrawler,
                                 PolicyChangeDetectionService policyChangeDetectionService,
                                 PolicySnapshotRepository policySnapshotRepository,
                                 RiskPipelineService riskPipelineService) {
        this.policyBodyCrawler = policyBodyCrawler;
        this.policyChangeDetectionService = policyChangeDetectionService;
        this.policySnapshotRepository = policySnapshotRepository;
        this.riskPipelineService = riskPipelineService;
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
     */
    @Transactional
    public CompanyCrawlResult processCompany(Company company) {
        boolean isFirstCollection = policySnapshotRepository
                .findFirstByCompany_CompanyIdOrderByCrawledAtDesc(company.getCompanyId())
                .isEmpty();

        String rawText = policyBodyCrawler.fetchCleanText(company.getPrivacyUrl());
        PolicySnapshot snapshot = policyChangeDetectionService.detectAndSave(company, rawText);

        boolean shouldAnalyze = isFirstCollection || snapshot.isChanged();
        if (shouldAnalyze) {
            riskPipelineService.analyzeAndSaveRisk(company, rawText);
        }

        return new CompanyCrawlResult(company.getCompanyId(), company.getCompanyName(),
                snapshot.isChanged(), shouldAnalyze);
    }
}
