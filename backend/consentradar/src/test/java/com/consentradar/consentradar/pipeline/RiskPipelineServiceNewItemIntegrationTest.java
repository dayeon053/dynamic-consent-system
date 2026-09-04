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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    // 2차 호출의 필수 항목이 3차 호출에서 완전히 사라지는 경우 (소프트 삭제 검증용)
    private static final String THIRD_CALL_JSON = """
            {
              "companyName": "테스트기업",
              "consentItems": [
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

    @Autowired
    private com.consentradar.consentradar.api.PersonalRiskCalculator personalRiskCalculator;

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

    /**
     * [TODO 확정 회귀 테스트 — 소프트 삭제] 2차 호출까지 있던 REQUIRED 항목("서비스 이용을
     * 위한 필수 개인정보 수집")이 3차 호출 응답에서 완전히 사라지면, 하드 삭제되지 않고
     * active=false로만 바뀌어야 한다 — row 자체(및 그걸 참조할 수 있는 UserConsentCheck/
     * UserConsentHistory)는 그대로 남아야 한다.
     */
    @Test
    void analyzeAndSaveRisk_softDeletesItem_whenItDisappearsFromLaterCall() {
        when(llmClient.callWithPrompt(anyString()))
                .thenReturn(FIRST_CALL_JSON)
                .thenReturn(SECOND_CALL_JSON)
                .thenReturn(THIRD_CALL_JSON);

        Company company = companyRepository.findById(testCompanyId).orElseThrow();

        riskPipelineService.analyzeAndSaveRisk(company, "1차 크롤링 본문");
        riskPipelineService.analyzeAndSaveRisk(company, "2차 크롤링 본문");
        List<ConsentItem> beforeThirdCall = consentItemRepository.findByCompany_CompanyId(testCompanyId);
        assertEquals(3, beforeThirdCall.size(), "2차 호출까지는 3건이어야 한다");
        Long requiredItemId = beforeThirdCall.stream()
                .filter(i -> i.getItemName().equals("서비스 이용을 위한 필수 개인정보 수집"))
                .findFirst().orElseThrow().getConsentItemId();

        riskPipelineService.analyzeAndSaveRisk(company, "3차 크롤링 본문");

        // 하드 삭제가 아니므로 row 개수는 그대로 3건이어야 한다
        List<ConsentItem> allAfterThirdCall = consentItemRepository.findByCompany_CompanyId(testCompanyId);
        assertEquals(3, allAfterThirdCall.size(), "소프트 삭제이므로 row가 물리적으로 지워지면 안 된다");

        ConsentItem deactivatedItem = allAfterThirdCall.stream()
                .filter(i -> i.getConsentItemId().equals(requiredItemId))
                .findFirst().orElseThrow();
        assertFalse(deactivatedItem.isActive(),
                "3차 호출에 없는 예전 REQUIRED 항목은 active=false로 바뀌어야 한다");

        // 활성 조회는 나머지 2건(3차 호출에 실제로 존재한 항목)만 반환해야 한다
        List<ConsentItem> activeAfterThirdCall = consentItemRepository.findByCompany_CompanyIdAndActiveTrue(testCompanyId);
        assertEquals(2, activeAfterThirdCall.size(), "active=true 조회는 3차 호출에 매칭된 2건만 나와야 한다");
        assertTrue(activeAfterThirdCall.stream().noneMatch(i -> i.getConsentItemId().equals(requiredItemId)),
                "비활성화된 항목은 active=true 조회 결과에 포함되면 안 된다");
    }

    /**
     * [TODO 확정 회귀 테스트 — 위험도 계산에서 제외] 소프트 삭제된 REQUIRED 항목은
     * PersonalRiskCalculator.calculate()에서도 제외돼야 한다. 3차 호출 후 이 기업의 유일한
     * REQUIRED 항목이 비활성화되므로("필수동의조차 없는" 상태), calculate()는 방어 코드에
     * 따라 null을 반환해야 한다 — 만약 비활성화된 REQUIRED 항목이 계속 계산에 섞여 들어간다면
     * null이 아니라 그 예전 항목 기준 점수가 나올 것이다.
     */
    @Test
    void calculate_excludesSoftDeletedRequiredItem_afterItDisappearsFromLaterCall() {
        when(llmClient.callWithPrompt(anyString()))
                .thenReturn(FIRST_CALL_JSON)
                .thenReturn(SECOND_CALL_JSON)
                .thenReturn(THIRD_CALL_JSON);

        Company company = companyRepository.findById(testCompanyId).orElseThrow();
        long noSuchUserId = 999_999_999L;

        riskPipelineService.analyzeAndSaveRisk(company, "1차 크롤링 본문");
        assertNotNull(personalRiskCalculator.calculate(noSuchUserId, testCompanyId),
                "1차 호출 직후엔 REQUIRED 항목이 있으므로 계산 결과가 있어야 한다");

        riskPipelineService.analyzeAndSaveRisk(company, "2차 크롤링 본문");
        riskPipelineService.analyzeAndSaveRisk(company, "3차 크롤링 본문");

        assertNull(personalRiskCalculator.calculate(noSuchUserId, testCompanyId),
                "3차 호출로 유일한 REQUIRED 항목이 소프트 삭제됐으므로, 그 항목이 계산에서 "
                        + "제외되어 '필수동의 없음' 방어 코드가 null을 반환해야 한다");
    }
}
