package com.consentradar.consentradar.pipeline;

import com.consentradar.consentradar.crawler.LlmClient;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * analyzeAndSaveRisk()의 itemName 기준 upsert 로직이, LLM 응답에 "이전 호출과 다른 새
 * itemName"이 섞여 있을 때도 기존 항목은 update / 새 항목만 insert하는지 검증한다.
 * {@link RiskPipelineServiceIntegrationTest}는 두 호출이 완전히 같은 mock 응답(기존 항목
 * 재사용)만 검증하므로, 그와 별개로 "항목 구성이 실제로 달라지는" 케이스를 여기서 다룬다.
 *
 * LlmClient를 {@link MockitoBean}으로 교체해서 호출마다 다른 응답을 주기 때문에, 이 클래스는
 * 자체 Spring 컨텍스트를 쓴다 — 다른 통합 테스트 클래스와 mock 상태를 공유하지 않기 위해
 * 별도 파일로 분리했다. 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만
 * 실행된다.
 */
@Tag("integration")
@SpringBootTest
class RiskPipelineServiceNewItemIntegrationTest {

    private static final String TEST_PACKAGE_NAME = "test.pipeline.newitem.integration";

    private static final String FIRST_CALL_JSON = """
            {
              "companyName": "테스트기업",
              "consentItems": [
                {
                  "itemName": "서비스 이용을 위한 필수 개인정보 수집",
                  "itemType": "REQUIRED",
                  "ds": "HIGH", "es": "MEDIUM", "tf": "LONG", "pc": "COMPLIANT", "ai": "LOW_RISK",
                  "dsReason": "r", "esReason": "r", "tfReason": "r", "pcReason": "r", "aiReason": "r"
                },
                {
                  "itemName": "마케팅 정보 수신 동의",
                  "itemType": "OPTIONAL",
                  "ds": "MODERATE", "es": "HIGH", "tf": "LONG", "pc": "NON_COMPLIANT", "ai": "HIGH_RISK",
                  "dsReason": "r", "esReason": "r", "tfReason": "r", "pcReason": "r", "aiReason": "r"
                }
              ]
            }
            """;

    // 기존 두 항목(itemName 동일, 점수만 변경) + 이전 호출엔 없던 새 itemName 하나
    private static final String SECOND_CALL_JSON = """
            {
              "companyName": "테스트기업",
              "consentItems": [
                {
                  "itemName": "서비스 이용을 위한 필수 개인정보 수집",
                  "itemType": "REQUIRED",
                  "ds": "MODERATE", "es": "MEDIUM", "tf": "LONG", "pc": "COMPLIANT", "ai": "LOW_RISK",
                  "dsReason": "r", "esReason": "r", "tfReason": "r", "pcReason": "r", "aiReason": "r"
                },
                {
                  "itemName": "마케팅 정보 수신 동의",
                  "itemType": "OPTIONAL",
                  "ds": "MODERATE", "es": "HIGH", "tf": "LONG", "pc": "NON_COMPLIANT", "ai": "HIGH_RISK",
                  "dsReason": "r", "esReason": "r", "tfReason": "r", "pcReason": "r", "aiReason": "r"
                },
                {
                  "itemName": "위치정보 기반 서비스 제공 동의",
                  "itemType": "OPTIONAL",
                  "ds": "LOW", "es": "LOW", "tf": "SHORT", "pc": "COMPLIANT", "ai": "LOW_RISK",
                  "dsReason": "r", "esReason": "r", "tfReason": "r", "pcReason": "r", "aiReason": "r"
                }
              ]
            }
            """;

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

    @MockitoBean
    private LlmClient llmClient;

    private Long testCompanyId;

    @BeforeEach
    void setUp() {
        companyRepository.findByPackageName(TEST_PACKAGE_NAME).ifPresent(this::cleanupCompany);

        Company company = new Company();
        company.setCompanyName("신규항목테스트기업");
        company.setLegalName("신규항목테스트기업");
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
    void analyzeAndSaveRisk_insertsOnlyTheNewItem_whenSecondCallAddsAnUnseenItemName() {
        when(llmClient.callWithPrompt(anyString()))
                .thenReturn(FIRST_CALL_JSON)
                .thenReturn(SECOND_CALL_JSON);

        Company company = companyRepository.findById(testCompanyId).orElseThrow();

        riskPipelineService.analyzeAndSaveRisk(company, "1차 크롤링 본문");
        List<ConsentItem> afterFirstCall = consentItemRepository.findByCompany_CompanyId(testCompanyId);
        assertEquals(2, afterFirstCall.size(), "1차 호출은 항목 2건이어야 한다");
        Map<String, Long> idsAfterFirstCall = afterFirstCall.stream()
                .collect(Collectors.toMap(ConsentItem::getItemName, ConsentItem::getConsentItemId));

        riskPipelineService.analyzeAndSaveRisk(company, "2차 크롤링 본문");
        List<ConsentItem> afterSecondCall = consentItemRepository.findByCompany_CompanyId(testCompanyId);

        assertEquals(3, afterSecondCall.size(), "새 itemName 1건이 추가되어 총 3건이어야 한다");

        Map<String, ConsentItem> byNameAfterSecondCall = afterSecondCall.stream()
                .collect(Collectors.toMap(ConsentItem::getItemName, item -> item));

        // 기존 2개 항목은 새 row가 아니라 같은 row가 갱신되어야 한다 (consentItemId 불변)
        for (Map.Entry<String, Long> entry : idsAfterFirstCall.entrySet()) {
            assertTrue(byNameAfterSecondCall.containsKey(entry.getKey()),
                    "기존 itemName [" + entry.getKey() + "]이 사라지면 안 된다");
            assertEquals(entry.getValue(), byNameAfterSecondCall.get(entry.getKey()).getConsentItemId(),
                    "기존 항목 [" + entry.getKey() + "]은 삭제 후 재삽입이 아니라 같은 row가 갱신되어야 한다");
        }

        // 새 itemName은 이전 호출에 없던 새 row여야 한다
        ConsentItem newItem = byNameAfterSecondCall.get("위치정보 기반 서비스 제공 동의");
        assertTrue(newItem != null && !idsAfterFirstCall.containsValue(newItem.getConsentItemId()),
                "새 itemName은 새로 insert된 row여야 한다");

        // 기존 항목의 점수는 2차 호출 값으로 갱신됐어야 한다 (필수 항목 ds: HIGH(5점) -> MODERATE(3점))
        ConsentItem updatedRequiredItem = byNameAfterSecondCall.get("서비스 이용을 위한 필수 개인정보 수집");
        assertEquals(3, updatedRequiredItem.getDsScore(), "2차 호출의 갱신된 점수(MODERATE=3)가 반영되어야 한다");
    }
}
