package com.consentradar.consentradar.pipeline;

import com.consentradar.consentradar.consentitem.ConsentItemUpsertService;
import com.consentradar.consentradar.crawler.CrawlTarget;
import com.consentradar.consentradar.crawler.CrawledPolicyDto;
import com.consentradar.consentradar.crawler.LlmClient;
import com.consentradar.consentradar.crawler.PolicyBodyCrawler;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 전체 위험도 산출 파이프라인 오케스트레이터
 *
 * [파이프라인 흐름]
 * PolicyBodyCrawler (크롤링)
 *   → LlmPromptTemplate (프롬프트 생성)
 *   → LlmRetryModule + LlmClient (LLM 호출 + 재시도)
 *   → LlmResponseParser (파싱 + 검증) — LlmRetryModule 내부에서 자동 수행
 *   → RiskCalculator (위험도 점수 산출)
 *   → DB 저장 (PolicySnapshot + RiskScore)
 */
@Service
public class RiskPipelineService {

    private final PolicyBodyCrawler policyBodyCrawler;
    private final LlmClient llmClient;
    private final PolicySnapshotRepository policySnapshotRepository;
    private final ConsentItemUpsertService consentItemUpsertService;
    private final RiskScoreRepository riskScoreRepository;

    public RiskPipelineService(PolicyBodyCrawler policyBodyCrawler,
                               LlmClient llmClient,
                               PolicySnapshotRepository policySnapshotRepository,
                               ConsentItemUpsertService consentItemUpsertService,
                               RiskScoreRepository riskScoreRepository) {
        this.policyBodyCrawler = policyBodyCrawler;
        this.llmClient = llmClient;
        this.policySnapshotRepository = policySnapshotRepository;
        this.consentItemUpsertService = consentItemUpsertService;
        this.riskScoreRepository = riskScoreRepository;
    }

    /**
     * 크롤링부터 위험도 DB 저장까지 전체 파이프라인을 실행한다.
     * 크롤링은 재시도(3회, 1s→2s→4s backoff)와 silent-failure 방지 로직을 갖춘
     * {@link PolicyBodyCrawler}를 사용한다.
     *
     * @param target  크롤링 대상 (기업명, URL)
     * @param company 기업 엔티티 (이미 DB에 저장된 상태여야 함)
     * @return 저장된 RiskScore 목록
     */
    @Transactional
    public List<RiskScore> run(CrawlTarget target, Company company) {
        // 1. 크롤링
        System.out.println("[Pipeline] 1단계: 크롤링 시작 — " + target.getCompanyName());
        String rawText = policyBodyCrawler.fetchCleanText(target.getPolicyUrl());
        CrawledPolicyDto crawled = new CrawledPolicyDto(
                target.getCompanyName(), target.getPolicyUrl(), rawText, OffsetDateTime.now());
        System.out.println("[Pipeline] 크롤링 완료. 텍스트 길이: " + crawled.getRawText().length() + "자");

        return runWithCrawledPolicy(target, company, crawled);
    }

    /**
     * 이미 수집된(또는 목업) 크롤링 결과를 받아 PolicySnapshot 저장 + 파싱 → 위험도 산출 →
     * DB 저장을 수행한다. 실제 네트워크 크롤링 없이 파이프라인을 검증하고 싶을 때 사용한다.
     */
    @Transactional
    public List<RiskScore> runWithCrawledPolicy(CrawlTarget target, Company company, CrawledPolicyDto crawled) {
        // 2. PolicySnapshot 저장
        PolicySnapshot snapshot = new PolicySnapshot();
        snapshot.setCompany(company);
        snapshot.setRawText(crawled.getRawText());
        snapshot.setChanged(false);
        snapshot.setCrawledAt(LocalDateTime.now());
        policySnapshotRepository.save(snapshot);
        System.out.println("[Pipeline] 2단계: PolicySnapshot 저장 완료");

        return analyzeAndSaveRisk(company, crawled.getRawText());
    }

    /**
     * PolicySnapshot 저장 없이 LLM 파싱 → 위험도 산출 → ConsentItem/RiskScore 저장만 수행한다.
     * 스냅샷 저장(변경 여부 판단 포함)을 이미 {@link com.consentradar.consentradar.crawler.PolicyChangeDetectionService}가
     * 담당하는 흐름(스케줄러/관리자 수동 트리거)에서, 스냅샷을 중복 저장하지 않고 이 메서드만
     * 호출하기 위해 분리했다.
     *
     * @param company 기업 엔티티 (이미 DB에 저장된 상태여야 함)
     * @param rawText 크롤링된(또는 목업) 약관 원문 텍스트
     * @return 저장된 RiskScore 목록 (항목별 N건 + 기업 대표 1건)
     */
    @Transactional
    public List<RiskScore> analyzeAndSaveRisk(Company company, String rawText) {
        // 3. LLM 프롬프트 생성
        String prompt = LlmPromptTemplate.buildAnalysisPrompt(company.getCompanyName(), rawText);
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
        List<com.dynamicconsent.model.RiskInput> riskInputs = new ArrayList<>();
        java.util.Set<String> matchedItemNames = new java.util.HashSet<>();

        for (ConsentItemAnalysis item : llmResponse.getConsentItems()) {
            matchedItemNames.add(item.getItemName());
            com.dynamicconsent.model.RiskInput riskInput = item.toRiskInput();
            riskInputs.add(riskInput);
            RiskResult result = RiskCalculator.calculate(riskInput);

            System.out.printf("[Pipeline]   항목: %-30s | 점수: %5.1f | 등급: %s(%s)%n",
                    item.getItemName(), result.getScore(),
                    result.getGrade().englishLabel, result.getGrade().koreanLabel);

            // ConsentItem upsert: itemName 기준으로 기존 항목이면 UserConsentCheck를 유지한 채
            // 점수/타입만 갱신하고, 없으면 새로 만든다 (ConsentItemUpsertService, DB unique
            // 제약(company_id, item_name)으로 동시 요청에서도 중복 insert가 나지 않는다).
            consentItemUpsertService.upsert(
                    company,
                    item.getItemName(),
                    ConsentItem.ItemType.valueOf(item.getItemType()),
                    riskInput.getDataSensitivity().score,
                    riskInput.getExposureScope().score,
                    riskInput.getTimeFactor().score,
                    riskInput.getPurposeClarity().score,
                    riskInput.getAiRiskFactor().score);

            // 항목별 RiskScore 저장 (isRepresentative=false 기본값)
            RiskScore riskScore = new RiskScore();
            riskScore.setUser(null);
            riskScore.setCompany(company);
            riskScore.setTotalScore(BigDecimal.valueOf(result.getScore()));
            riskScore.setGrade(RiskScore.Grade.valueOf(result.getGrade().name()));
            riskScore.setScoredAt(LocalDate.now());
            riskScore.setRepresentative(false);

            savedScores.add(riskScoreRepository.save(riskScore));
        }

        System.out.println("[Pipeline] 항목별 RiskScore 저장 완료: " + savedScores.size() + "건");

        // 5-1. 이번 크롤링 결과에 더 이상 나타나지 않는 기존 항목은 소프트 삭제(active=false)
        // 한다 — UserConsentCheck/UserConsentHistory가 참조 중일 수 있어 하드 삭제하지 않는다.
        consentItemUpsertService.deactivateMissing(company, matchedItemNames);
        System.out.println("[Pipeline] 5-1단계: 이번 결과에 없는 예전 항목 소프트 삭제 완료");

        // 6. 기업 대표 위험도 산출(최고 점수) + RiskScore 저장 (isRepresentative=true)
        // TODO(2026-07-26, 다연 확인 필요): 개인 맞춤 위험도(PersonalRiskCalculator.calculate)는
        // combineImpacts(변수별 최댓값 합성)로 정본 통일됨. 여기는 개인 맞춤이 아니라 "기업 전체
        // (배치) 대표 점수" 계산이라 성격이 달라 calculateMax를 그대로 두었다 — 이것도
        // combineImpacts로 통일해야 하는지는 별도 팀 결정 필요.
        System.out.println("[Pipeline] 6단계: 기업 대표 위험도 산출 시작");
        RiskResult companyResult = RiskCalculator.calculateMax(riskInputs);

        RiskScore companyRiskScore = new RiskScore();
        companyRiskScore.setUser(null);
        companyRiskScore.setCompany(company);
        companyRiskScore.setTotalScore(BigDecimal.valueOf(companyResult.getScore()));
        companyRiskScore.setGrade(RiskScore.Grade.valueOf(companyResult.getGrade().name()));
        companyRiskScore.setScoredAt(LocalDate.now());
        companyRiskScore.setRepresentative(true);
        savedScores.add(riskScoreRepository.save(companyRiskScore));

        System.out.printf("[Pipeline]   기업 대표 점수: %5.1f | 등급: %s(%s)%n",
                companyResult.getScore(), companyResult.getGrade().englishLabel, companyResult.getGrade().koreanLabel);
        System.out.println("[Pipeline] 완료. 저장된 RiskScore 총 " + savedScores.size() + "건 (항목별 + 기업 대표 1건)");
        return savedScores;
    }
}
