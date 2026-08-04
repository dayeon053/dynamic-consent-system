package com.consentradar.consentradar.scheduler;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.repository.CompanyRepository;
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
 * 기업 1건에 대한 실제 처리(크롤링+변경감지+조건부 위험도재산출, 트랜잭션 경계 포함)는
 * {@link PolicyCrawlProcessor}에 위임한다 — 이 클래스 자체는 트랜잭션이 없는 얇은
 * 오케스트레이터다.
 */
@Component
public class PolicyCrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolicyCrawlScheduler.class);

    private final CompanyRepository companyRepository;
    private final PolicyCrawlProcessor policyCrawlProcessor;

    public PolicyCrawlScheduler(CompanyRepository companyRepository,
                                 PolicyCrawlProcessor policyCrawlProcessor) {
        this.companyRepository = companyRepository;
        this.policyCrawlProcessor = policyCrawlProcessor;
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
                CompanyCrawlResult result = policyCrawlProcessor.processCompany(company);
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
        return policyCrawlProcessor.processCompany(company);
    }
}
