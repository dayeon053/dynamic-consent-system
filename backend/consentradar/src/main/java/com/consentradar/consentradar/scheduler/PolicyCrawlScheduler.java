package com.consentradar.consentradar.scheduler;

import com.consentradar.consentradar.crawler.PolicyBodyCrawler;
import com.consentradar.consentradar.crawler.PolicyChangeDetectionService;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.pipeline.RiskPipelineService;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 매일 새벽 3시에 전체 기업의 개인정보처리방침을 크롤링하고, 변경 여부를 감지한 뒤,
 * 최초 수집이거나 실제로 변경된 기업에 한해 LLM 파싱→위험도 산출→DB 저장까지 이어서 실행하는
 * 파이프라인. 관리자 API 등에서 즉시 재사용할 수 있도록 실행 로직을 {@link #runPipeline()}과
 * {@link #runForCompany(Long)}으로 분리했다.
 *
 * 변경이 없는 기업까지 매번 LLM을 호출하지 않도록, 위험도 재산출은 "최초 수집이거나
 * 약관 내용이 실제로 바뀐 경우"에만 트리거한다.
 */
@Component
public class PolicyCrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolicyCrawlScheduler.class);

    private final CompanyRepository companyRepository;
    private final PolicyBodyCrawler policyBodyCrawler;
    private final PolicyChangeDetectionService policyChangeDetectionService;
    private final PolicySnapshotRepository policySnapshotRepository;
    private final RiskPipelineService riskPipelineService;

    public PolicyCrawlScheduler(CompanyRepository companyRepository,
                                 PolicyBodyCrawler policyBodyCrawler,
                                 PolicyChangeDetectionService policyChangeDetectionService,
                                 PolicySnapshotRepository policySnapshotRepository,
                                 RiskPipelineService riskPipelineService) {
        this.companyRepository = companyRepository;
        this.policyBodyCrawler = policyBodyCrawler;
        this.policyChangeDetectionService = policyChangeDetectionService;
        this.policySnapshotRepository = policySnapshotRepository;
        this.riskPipelineService = riskPipelineService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledRun() {
        runPipeline();
    }

    /**
     * 크롤링(태스크2) + 변경 감지(태스크3) + 조건부 위험도 재산출을 묶어 전체 기업에 대해 실행한다.
     * 스케줄러뿐 아니라 관리자 API 등에서 수동 트리거 용도로도 호출 가능하다.
     */
    public PipelineRunResult runPipeline() {
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("[Pipeline] 정책 크롤링+변경감지+위험도재산출 파이프라인 시작 — {}", startedAt);

        List<Company> companies = companyRepository.findAll();
        int successCount = 0;
        int failCount = 0;
        int riskAnalysisCount = 0;

        for (Company company : companies) {
            try {
                CompanyCrawlResult result = processCompany(company);
                if (result.riskAnalysisTriggered()) {
                    riskAnalysisCount++;
                }
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("[Pipeline] {} 처리 실패: {}", company.getCompanyName(), e.getMessage());
            }
        }

        LocalDateTime finishedAt = LocalDateTime.now();
        log.info("[Pipeline] 파이프라인 종료 — {} | 처리 기업 수: {} | 성공: {} | 실패: {} | 위험도 재산출: {} | 소요시간: {}ms",
                finishedAt, companies.size(), successCount, failCount, riskAnalysisCount,
                Duration.between(startedAt, finishedAt).toMillis());

        return new PipelineRunResult(startedAt, finishedAt, companies.size(), successCount, failCount);
    }

    /**
     * 특정 기업 1건만 즉시 크롤링+변경감지+조건부 위험도 재산출을 실행한다.
     * 관리자 수동 트리거 API에서 사용한다.
     */
    public CompanyCrawlResult runForCompany(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 companyId: " + companyId));
        return processCompany(company);
    }

    /**
     * 크롤링 → 변경감지 → (최초 수집이거나 변경 있으면) 위험도 재산출을 수행한다.
     */
    private CompanyCrawlResult processCompany(Company company) {
        boolean isFirstCollection = policySnapshotRepository
                .findFirstByCompany_CompanyIdOrderByCrawledAtDesc(company.getCompanyId())
                .isEmpty();

        String rawText = policyBodyCrawler.fetchCleanText(company.getPrivacyUrl());
        // [알려진 이슈] detectAndSave()는 별도 @Transactional이라 여기서 스냅샷이 이미 커밋된다.
        // 아래 analyzeAndSaveRisk()가 실패해도(예: LLM 재시도 소진) 이 커밋은 되돌릴 수 없어,
        // "스냅샷은 최신인데 위험도는 재산출 안 됨" 상태가 남을 수 있다. 재설계 제안은
        // docs/known_issues.md 참고 (다연과 논의 후 결정 — 지금은 회귀 감지 테스트만 있음:
        // PolicyCrawlSchedulerTransactionBoundaryIntegrationTest).
        PolicySnapshot snapshot = policyChangeDetectionService.detectAndSave(company, rawText);

        boolean shouldAnalyze = isFirstCollection || snapshot.isChanged();
        if (shouldAnalyze) {
            riskPipelineService.analyzeAndSaveRisk(company, rawText);
        }

        return new CompanyCrawlResult(company.getCompanyId(), company.getCompanyName(),
                snapshot.isChanged(), shouldAnalyze);
    }
}
