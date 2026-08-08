package com.consentradar.consentradar.scheduler;

import com.consentradar.consentradar.crawler.LlmClient;
import com.consentradar.consentradar.crawler.PolicyBodyCrawler;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.PolicySnapshot;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * [트랜잭션 경계 통합 확인용 — 2026-07-30, docs/known_issues.md "PolicySnapshot 저장과 위험도
 * 재산출의 트랜잭션 경계 불일치" 해결] {@code PolicyCrawlProcessor.processCompany()}가
 * {@code @Transactional}로 스냅샷 저장({@code PolicyChangeDetectionService.detectAndSave()})과
 * 위험도 재산출({@code RiskPipelineService.analyzeAndSaveRisk()})을 하나의 트랜잭션으로 묶는다.
 *
 * 이전에는 두 호출이 각각 별도 트랜잭션이라, 위험도 재산출이 실패해도 이미 커밋된 스냅샷은
 * 되돌릴 수 없었다(회귀 감지 테스트로 그 동작을 문서화해뒀었다). 지금은 반대로 "위험도 재산출이
 * 실패하면 스냅샷 저장도 함께 롤백되어야 한다"를 검증한다. 기본 `./gradlew test`에서는 제외되고
 * `./gradlew integrationTest`로만 실행된다.
 */
@Tag("integration")
@SpringBootTest
class PolicyCrawlSchedulerTransactionBoundaryIntegrationTest {

    private static final String TEST_PACKAGE_NAME = "test.scheduler.txboundary.integration";

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PolicySnapshotRepository policySnapshotRepository;

    @Autowired
    private ConsentItemRepository consentItemRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private PolicyCrawlScheduler policyCrawlScheduler;

    @MockitoBean
    private PolicyBodyCrawler policyBodyCrawler;

    @MockitoBean
    private LlmClient llmClient;

    private Long testCompanyId;

    @BeforeEach
    void setUp() {
        companyRepository.findByPackageName(TEST_PACKAGE_NAME).ifPresent(this::cleanupCompany);

        Company company = new Company();
        company.setCompanyName("트랜잭션경계테스트기업");
        company.setLegalName("트랜잭션경계테스트기업");
        company.setCategory("기타");
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
    void runForCompany_rollsBackPolicySnapshotToo_whenRiskAnalysisFailsAfterSnapshotSaved() {
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("정책 원문");
        // LLM이 계속 파싱 불가능한 응답을 반환 -> LlmRetryModule 3회 재시도 모두 실패
        // -> analyzeAndSaveRisk()가 RuntimeException을 던짐
        when(llmClient.callWithPrompt(anyString())).thenReturn("이건 JSON이 아닙니다");

        assertThrows(RuntimeException.class, () -> policyCrawlScheduler.runForCompany(testCompanyId));

        // PolicyCrawlProcessor.processCompany()가 스냅샷 저장 + 위험도 재산출을 하나의
        // 트랜잭션으로 묶으므로, 위험도 재산출 실패 시 스냅샷 저장도 함께 롤백되어 남지 않아야
        // 한다 — "스냅샷은 최신인데 위험도는 비어있는" 상태가 더 이상 남지 않는다.
        Optional<PolicySnapshot> snapshot =
                policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(testCompanyId);
        assertTrue(snapshot.isEmpty(),
                "위험도 산출이 실패하면 같은 트랜잭션으로 묶인 PolicySnapshot 저장도 롤백되어야 한다");

        // ConsentItem/RiskScore는 analyzeAndSaveRisk() 실패로 저장되지 않아야 한다
        assertEquals(0, consentItemRepository.countByCompany_CompanyId(testCompanyId));
        assertEquals(0, riskScoreRepository.findByCompany_CompanyId(testCompanyId).size());
    }
}
