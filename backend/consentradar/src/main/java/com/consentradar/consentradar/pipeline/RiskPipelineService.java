package com.consentradar.consentradar.pipeline;

import com.consentradar.consentradar.crawler.CrawlTarget;
import com.consentradar.consentradar.crawler.CrawledPolicyDto;
import com.consentradar.consentradar.crawler.LlmClient;
import com.consentradar.consentradar.crawler.PolicyCrawler;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.entity.RiskScore;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import com.consentradar.consentradar.repository.RiskScoreRepository;
import com.dynamicconsent.algorithm.RiskCalculator;
import com.dynamicconsent.llm.dto.ConsentItemAnalysis;
import com.dynamicconsent.llm.dto.LlmRiskAnalysisResponse;
import com.dynamicconsent.llm.exception.LlmRetryExhaustedException;
import com.dynamicconsent.llm.prompt.LlmPromptTemplate;
import com.dynamicconsent.llm.retry.LlmRetryModule;
import com.dynamicconsent.model.RiskResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 전체 위험도 산출 파이프라인 오케스트레이터
 *
 * [파이프라인 흐름]
 * PolicyCrawler (크롤링)
 *   → LlmPromptTemplate (프롬프트 생성)
 *   → LlmRetryModule + LlmClient (LLM 호출 + 재시도)
 *   → LlmResponseParser (파싱 + 검증) — LlmRetryModule 내부에서 자동 수행
 *   → RiskCalculator (위험도 점수 산출)
 *   → DB 저장 (PolicySnapshot + RiskScore)
 */
@Service
public class RiskPipelineService {

    private final PolicyCrawler policyCrawler;
    private final LlmClient llmClient;
    private final PolicySnapshotRepository policySnapshotRepository;
    private final RiskScoreRepository riskScoreRepository;

    public RiskPipelineService(PolicyCrawler policyCrawler,
                               LlmClient llmClient,
                               PolicySnapshotRepository policySnapshotRepository,
                               RiskScoreRepository riskScoreRepository) {
        this.policyCrawler = policyCrawler;
        this.llmClient = llmClient;
        this.policySnapshotRepository = policySnapshotRepository;
        this.riskScoreRepository = riskScoreRepository;
    }

    /**
     * 크롤링부터 위험도 DB 저장까지 전체 파이프라인을 실행한다.
     *
     * @param target  크롤링 대상 (URL, CSS 셀렉터 등)
     * @param company 기업 엔티티 (이미 DB에 저장된 상태여야 함)
     * @return 저장된 RiskScore 목록
     */
    @Transactional
    public List<RiskScore> run(CrawlTarget target, Company company) {
        // 1. 크롤링
        System.out.println("[Pipeline] 1단계: 크롤링 시작 — " + target.getCompanyName());
        CrawledPolicyDto crawled = policyCrawler.crawl(target);
        System.out.println("[Pipeline] 크롤링 완료. 텍스트 길이: " + crawled.getRawText().length() + "자");

        // 2. PolicySnapshot 저장
        PolicySnapshot snapshot = new PolicySnapshot();
        snapshot.setCompany(company);
        snapshot.setRawText(crawled.getRawText());
        snapshot.setChanged(false);
        snapshot.setCrawledAt(LocalDateTime.now());
        policySnapshotRepository.save(snapshot);
        System.out.println("[Pipeline] 2단계: PolicySnapshot 저장 완료");

        // 3. LLM 프롬프트 생성
        String prompt = LlmPromptTemplate.buildAnalysisPrompt(
                target.getCompanyName(), crawled.getRawText());
        System.out.println("[Pipeline] 3단계: LLM 프롬프트 생성 완료");

        // 4. LLM 호출 + 파싱 (실패 시 자동 재시도)
        System.out.println("[Pipeline] 4단계: LLM 호출 시작 (최대 3회 재시도)");
        LlmRiskAnalysisResponse llmResponse;
        try {
            llmResponse = LlmRetryModule.execute(prompt, llmClient::callWithPrompt);
        } catch (LlmRetryExhaustedException e) {
            System.out.println("[Pipeline] LLM 파싱 " + e.getAttemptCount() + "회 모두 실패: " + e.getMessage());
            throw new RuntimeException("LLM 파이프라인 실패: " + e.getMessage(), e);
        }
        System.out.println("[Pipeline] LLM 파싱 성공. 동의항목 수: " + llmResponse.getConsentItems().size() + "개");

        // 5. 동의항목별 위험도 산출 + RiskScore 저장
        System.out.println("[Pipeline] 5단계: 위험도 산출 시작");
        List<RiskScore> savedScores = new ArrayList<>();

        for (ConsentItemAnalysis item : llmResponse.getConsentItems()) {
            RiskResult result = RiskCalculator.calculate(item.toRiskInput());

            System.out.printf("[Pipeline]   항목: %-30s | 점수: %5.1f | 등급: %s(%s)%n",
                    item.getItemName(), result.getScore(),
                    result.getGrade().englishLabel, result.getGrade().koreanLabel);

            RiskScore riskScore = new RiskScore();
            riskScore.setUser(null); // 배치 크롤링 시 특정 유저 없음
            riskScore.setCompany(company);
            riskScore.setTotalScore(BigDecimal.valueOf(result.getScore()));
            riskScore.setGrade(RiskScore.Grade.valueOf(result.getGrade().name()));
            riskScore.setScoredAt(LocalDate.now());

            savedScores.add(riskScoreRepository.save(riskScore));
        }

        System.out.println("[Pipeline] 완료. 저장된 RiskScore: " + savedScores.size() + "건");
        return savedScores;
    }
}
