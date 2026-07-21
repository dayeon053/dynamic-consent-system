package com.consentradar.consentradar.scheduler;

import com.consentradar.consentradar.crawler.PolicyBodyCrawler;
import com.consentradar.consentradar.crawler.PolicyChangeDetectionService;
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
 * 매일 새벽 3시에 전체 기업의 개인정보처리방침을 크롤링하고 변경 여부를 감지하는 파이프라인.
 * 관리자 API 등에서 즉시 재사용할 수 있도록 실행 로직을 {@link #runPipeline()}으로 분리했다.
 */
@Component
public class PolicyCrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolicyCrawlScheduler.class);

    private final CompanyRepository companyRepository;
    private final PolicyBodyCrawler policyBodyCrawler;
    private final PolicyChangeDetectionService policyChangeDetectionService;

    public PolicyCrawlScheduler(CompanyRepository companyRepository,
                                 PolicyBodyCrawler policyBodyCrawler,
                                 PolicyChangeDetectionService policyChangeDetectionService) {
        this.companyRepository = companyRepository;
        this.policyBodyCrawler = policyBodyCrawler;
        this.policyChangeDetectionService = policyChangeDetectionService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledRun() {
        runPipeline();
    }

    /**
     * 크롤링(태스크2) + 변경 감지(태스크3)를 묶어 전체 기업에 대해 실행한다.
     * 스케줄러뿐 아니라 관리자 API 등에서 수동 트리거 용도로도 호출 가능하다.
     */
    public PipelineRunResult runPipeline() {
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("[Pipeline] 정책 크롤링+변경감지 파이프라인 시작 — {}", startedAt);

        List<Company> companies = companyRepository.findAll();
        int successCount = 0;
        int failCount = 0;

        for (Company company : companies) {
            try {
                String rawText = policyBodyCrawler.fetchCleanText(company.getPrivacyUrl());
                policyChangeDetectionService.detectAndSave(company, rawText);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("[Pipeline] {} 처리 실패: {}", company.getCompanyName(), e.getMessage());
            }
        }

        LocalDateTime finishedAt = LocalDateTime.now();
        log.info("[Pipeline] 파이프라인 종료 — {} | 처리 기업 수: {} | 성공: {} | 실패: {} | 소요시간: {}ms",
                finishedAt, companies.size(), successCount, failCount,
                Duration.between(startedAt, finishedAt).toMillis());

        return new PipelineRunResult(startedAt, finishedAt, companies.size(), successCount, failCount);
    }
}