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
 * [알려진 이슈 회귀 감지용] {@code PolicyChangeDetectionService.detectAndSave()}(스냅샷 저장)와
 * {@code RiskPipelineService.analyzeAndSaveRisk()}(위험도 재산출)는 별도의 {@code @Transactional}
 * 경계를 갖는다. {@code PolicyCrawlScheduler.processCompany()} 자체는 트랜잭션이 아니라서, 스냅샷이
 * 이미 커밋된 뒤 위험도 재산출이 실패해도 스냅샷 커밋은 되돌릴 수 없다.
 *
 * 이 테스트는 "이게 맞는 동작"이라고 주장하는 게 아니라, 지금 실제로 이렇게 동작한다는 걸
 * 문서화하는 회귀 감지용이다. 재설계 여부/방향은 docs/known_issues.md에 제안만 남겨뒀고
 * 팀 논의 후 결정할 사안이라 구현하지 않았다. 기본 `./gradlew test`에서는 제외되고
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
    void runForCompany_leavesPolicySnapshotCommitted_whenRiskAnalysisFailsAfterSnapshotSaved() {
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("정책 원문");
        // LLM이 계속 파싱 불가능한 응답을 반환 -> LlmRetryModule 3회 재시도 모두 실패
        // -> analyzeAndSaveRisk()가 RuntimeException을 던짐
        when(llmClient.callWithPrompt(anyString())).thenReturn("이건 JSON이 아닙니다");

        assertThrows(RuntimeException.class, () -> policyCrawlScheduler.runForCompany(testCompanyId));

        // 위험도 재산출은 실패했지만, 그 이전에 별도 트랜잭션으로 이미 커밋된 PolicySnapshot은
        // 그대로 남아있다 — 이게 바로 트랜잭션 경계 불일치 문제다. 다음 크롤링 때 이 스냅샷과
        // 해시가 같으면 "변경 없음"으로 판단되어, 이번에 실패한 위험도 재산출은 영구히
        // 재시도되지 않는다.
        Optional<PolicySnapshot> snapshot =
                policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(testCompanyId);
        assertTrue(snapshot.isPresent(),
                "[알려진 이슈] 위험도 산출이 실패해도 PolicySnapshot은 이미 커밋되어 남아있다 (docs/known_issues.md 참고)");
        assertEquals("정책 원문", snapshot.get().getRawText());

        // ConsentItem/RiskScore는 analyzeAndSaveRisk() 실패로 저장되지 않아야 한다
        assertEquals(0, consentItemRepository.countByCompany_CompanyId(testCompanyId));
        assertEquals(0, riskScoreRepository.findByCompany_CompanyId(testCompanyId).size());
    }
}
