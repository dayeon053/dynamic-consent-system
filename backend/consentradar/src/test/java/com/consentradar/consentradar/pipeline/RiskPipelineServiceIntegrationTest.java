package com.consentradar.consentradar.pipeline;

import com.consentradar.consentradar.crawler.CrawlTarget;
import com.consentradar.consentradar.crawler.CrawledPolicyDto;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.entity.RiskScore;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.ConsentItemRepository;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import com.consentradar.consentradar.repository.RiskScoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RiskPipelineService.runWithCrawledPolicy()가 크롤링 결과(mock)를 받아 LLM 파싱 →
 * 위험도 산출 → PolicySnapshot/ConsentItem/RiskScore 저장까지 전체 흐름을 실제 로컬
 * MySQL(consentradar DB)에 연결해 검증하는 통합 테스트. LlmClient는 기본 설정(llm.enabled=false)
 * 상 항상 고정 mock 응답을 반환하므로 실제 네트워크(크롤링/LLM) 없이 파이프라인 로직만 검증한다.
 * 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@Tag("integration")
@SpringBootTest
class RiskPipelineServiceIntegrationTest {

    private static final String TEST_PACKAGE_NAME = "test.pipeline.integration";

    private static final String MOCK_POLICY_TEXT = """
            테스트용 개인정보처리방침 목업 텍스트.
            제1조 회원가입 시 이름, 휴대폰번호, 이메일을 필수로 수집한다.
            제2조 마케팅 정보 수신에 동의한 회원에 한해 구매 이력을 활용한다.
            """.repeat(2);

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PolicySnapshotRepository policySnapshotRepository;

    @Autowired
    private ConsentItemRepository consentItemRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private RiskPipelineService riskPipelineService;

    private Long testCompanyId;

    @BeforeEach
    void setUp() {
        companyRepository.findByPackageName(TEST_PACKAGE_NAME).ifPresent(this::cleanupCompany);

        Company company = new Company();
        company.setCompanyName("파이프라인테스트기업");
        company.setPackageName(TEST_PACKAGE_NAME);
        company.setPrivacyUrl("https://example.com/privacy");
        company.setIsmsCertified(false);
        testCompanyId = companyRepository.save(company).getCompanyId();
    }

    @AfterEach
    void tearDown() {
        companyRepository.findById(testCompanyId).ifPresent(this::cleanupCompany);
    }

    private void cleanupCompany(Company company) {
        riskScoreRepository.deleteAll(riskScoreRepository.findByCompany_CompanyId(company.getCompanyId()));
        consentItemRepository.deleteAll(consentItemRepository.findByCompany_CompanyId(company.getCompanyId()));
        policySnapshotRepository.deleteAll(policySnapshotRepository.findByCompany_CompanyId(company.getCompanyId()));
        companyRepository.delete(company);
    }

    @Test
    void runWithCrawledPolicy_completesFullFlow_fromMockCrawlToDbSave() {
        Company company = companyRepository.findById(testCompanyId).orElseThrow();
        CrawlTarget target = new CrawlTarget(company.getCompanyName(), company.getPrivacyUrl(), "body");
        CrawledPolicyDto crawled = new CrawledPolicyDto(
                target.getCompanyName(), target.getPolicyUrl(), MOCK_POLICY_TEXT, OffsetDateTime.now());

        List<RiskScore> savedScores = riskPipelineService.runWithCrawledPolicy(target, company, crawled);

        // PolicySnapshot 저장 검증
        PolicySnapshot snapshot = policySnapshotRepository
                .findFirstByCompany_CompanyIdOrderByCrawledAtDesc(testCompanyId)
                .orElseThrow(() -> new AssertionError("PolicySnapshot이 저장되지 않았다"));
        assertEquals(MOCK_POLICY_TEXT, snapshot.getRawText());

        // ConsentItem 저장 검증 (LlmClient mock 응답 기준 필수 1 + 선택 1 = 2건)
        List<ConsentItem> savedItems = consentItemRepository.findByCompany_CompanyId(testCompanyId);
        assertEquals(2, savedItems.size());
        Set<ConsentItem.ItemType> itemTypes = savedItems.stream()
                .map(ConsentItem::getItemType)
                .collect(Collectors.toSet());
        assertEquals(Set.of(ConsentItem.ItemType.REQUIRED, ConsentItem.ItemType.OPTIONAL), itemTypes);

        // RiskScore 저장 검증: 항목별 2건 + 기업 대표 1건 = 3건
        assertEquals(3, savedScores.size());
        List<RiskScore> itemLevelScores = savedScores.stream().filter(s -> !s.isRepresentative()).toList();
        List<RiskScore> representativeScores = savedScores.stream().filter(RiskScore::isRepresentative).toList();
        assertEquals(2, itemLevelScores.size());
        assertEquals(1, representativeScores.size());

        // 대표 점수는 항목별 점수 중 최댓값과 일치해야 한다 (calculateMax 규약)
        double maxItemScore = itemLevelScores.stream()
                .max(Comparator.comparing(RiskScore::getTotalScore))
                .orElseThrow()
                .getTotalScore().doubleValue();
        assertEquals(maxItemScore, representativeScores.get(0).getTotalScore().doubleValue(), 1e-9);

        // DB에도 실제로 반영됐는지 재조회로 재확인
        List<RiskScore> scoresInDb = riskScoreRepository.findByCompany_CompanyId(testCompanyId);
        assertEquals(3, scoresInDb.size());
    }
}
