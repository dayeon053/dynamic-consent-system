package com.consentradar.consentradar.api;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.entity.RiskScore;
import com.consentradar.consentradar.entity.User;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.ConsentItemRepository;
import com.consentradar.consentradar.repository.RiskScoreRepository;
import com.consentradar.consentradar.repository.UserConsentCheckRepository;
import com.consentradar.consentradar.repository.UserConsentHistoryRepository;
import com.consentradar.consentradar.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConsentApiService.toggleConsent()가 PATCH를 여러 날에 걸쳐(또는 같은 날 여러 번) 호출해도
 * 개인 맞춤 위험도 히스토리(2-4 API, RiskScore isRepresentative=true)가 append-only로 쌓이는지
 * 실제 로컬 MySQL(consentradar DB)로 검증한다.
 *
 * [배경] 과거에는 toggleConsent()가 "가장 최근 대표 row"를 날짜 무관하게 찾아 덮어쓰고 그
 * row의 scoredAt까지 오늘로 바꿔버려서, 여러 날에 걸쳐 토글해도 실제로는 row가 항상 1개만
 * 남아 히스토리가 전혀 쌓이지 않는 버그가 있었다(docs/known_issues.md 참고). 이 테스트는 그
 * 버그가 재발하지 않는지 실제 DB로 고정한다. 다른 날짜의 row는 (테스트에서는 실제로 다른
 * 날짜가 되기를 기다릴 수 없으므로) 리포지토리로 직접 미리 심어 "이미 히스토리가 있는 상태"를
 * 시뮬레이션한다 — {@code PersonalRiskHistoryServiceIntegrationTest}의 기존 패턴과 동일하다.
 */
@Tag("integration")
@SpringBootTest
class ConsentApiServiceRiskHistoryIntegrationTest {

    private static final String TEST_EMAIL = "test.consentapi.riskhistory@example.com";
    private static final String TEST_PACKAGE_NAME = "test.consentapi.riskhistory";

    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private ConsentItemRepository consentItemRepository;
    @Autowired private RiskScoreRepository riskScoreRepository;
    @Autowired private UserConsentCheckRepository userConsentCheckRepository;
    @Autowired private UserConsentHistoryRepository userConsentHistoryRepository;
    @Autowired private ConsentApiService consentApiService;

    private Long userId;
    private Long companyId;
    private Long requiredItemId;

    @BeforeEach
    void setUp() {
        cleanUp();

        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setPassword("test-password");
        user.setNickname("PATCH히스토리테스트유저");
        userId = userRepository.save(user).getUserId();

        Company company = new Company();
        company.setCompanyName("PATCH히스토리테스트기업");
        company.setLegalName("PATCH히스토리테스트기업");
        company.setCategory("기타");
        company.setPackageName(TEST_PACKAGE_NAME);
        company.setPrivacyUrl("https://example.com/privacy");
        company.setIsmsCertified(false);
        companyId = companyRepository.save(company).getCompanyId();

        ConsentItem required = new ConsentItem();
        required.setCompany(company);
        required.setItemType(ConsentItem.ItemType.REQUIRED);
        required.setItemName("필수항목");
        required.setDsScore(3);
        required.setEsScore(1);
        required.setTfScore(1);
        required.setPcScore(1.0);
        required.setAiScore(1.0);
        requiredItemId = consentItemRepository.save(required).getConsentItemId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(user -> {
            userConsentHistoryRepository.findByUserIdOrderByChangedAtAsc(user.getUserId())
                    .forEach(userConsentHistoryRepository::delete);
            companyRepository.findByPackageName(TEST_PACKAGE_NAME).ifPresent(company -> {
                userConsentCheckRepository
                        .findAllByUser_UserIdAndConsentItem_Company_CompanyId(user.getUserId(), company.getCompanyId())
                        .forEach(userConsentCheckRepository::delete);
                riskScoreRepository
                        .findByUser_UserIdAndCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtAsc(
                                user.getUserId(), company.getCompanyId())
                        .forEach(riskScoreRepository::delete);
            });
            userRepository.delete(user);
        });
        companyRepository.findByPackageName(TEST_PACKAGE_NAME).ifPresent(companyRepository::delete);
    }

    @Test
    void toggleConsent_preservesPastDayHistory_whenTogglingOnANewDay() {
        // 어제 이미 저장된 히스토리 row를 직접 심어 "이전에 PATCH로 쌓인 히스토리가 있는 상태"를 재현한다.
        saveHistoricalRow(LocalDate.now().minusDays(1), 5.0, RiskScore.Grade.VERY_LOW);

        consentApiService.toggleConsent(userId, requiredItemId, true);

        List<RiskScore> history = riskScoreRepository
                .findByUser_UserIdAndCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtAsc(userId, companyId);

        assertEquals(2, history.size(), "어제 row는 그대로 남고 오늘자 row가 새로 추가돼야 한다(append-only)");
        assertEquals(LocalDate.now().minusDays(1), history.get(0).getScoredAt());
        assertEquals(0, BigDecimal.valueOf(5.0).compareTo(history.get(0).getTotalScore()),
                "과거 row의 점수는 오늘 PATCH로 인해 바뀌면 안 된다");
        assertEquals(LocalDate.now(), history.get(1).getScoredAt());
    }

    @Test
    void toggleConsent_upsertsTodayRow_whenCalledMultipleTimesOnSameDay() {
        consentApiService.toggleConsent(userId, requiredItemId, true);
        consentApiService.toggleConsent(userId, requiredItemId, false);
        consentApiService.toggleConsent(userId, requiredItemId, true);

        List<RiskScore> history = riskScoreRepository
                .findByUser_UserIdAndCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtAsc(userId, companyId);

        assertEquals(1, history.size(), "같은 날 여러 번 토글해도 오늘자 row는 1건으로 갱신되어야 한다");
        assertEquals(LocalDate.now(), history.get(0).getScoredAt());
    }

    @Test
    void toggleConsent_thenBatchSaveIfAbsent_doesNotOverwriteAlreadySavedTodayRow() {
        // PersonalRiskHistoryService.saveIfAbsent()가 나중에(같은 날 밤 배치로) 호출돼도
        // PATCH로 이미 저장된 오늘자 값을 덮어쓰지 않아야 한다(saveIfAbsent의 skip 의미론).
        consentApiService.toggleConsent(userId, requiredItemId, true);

        long countBefore = riskScoreRepository
                .countByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                        userId, companyId, LocalDate.now());
        assertEquals(1, countBefore);

        boolean stillExists = riskScoreRepository
                .findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                        userId, companyId, LocalDate.now())
                .isPresent();
        assertTrue(stillExists);
    }

    private void saveHistoricalRow(LocalDate scoredAt, double score, RiskScore.Grade grade) {
        User user = userRepository.findById(userId).orElseThrow();
        Company company = companyRepository.findById(companyId).orElseThrow();

        RiskScore riskScore = new RiskScore();
        riskScore.setUser(user);
        riskScore.setCompany(company);
        riskScore.setTotalScore(BigDecimal.valueOf(score));
        riskScore.setGrade(grade);
        riskScore.setScoredAt(scoredAt);
        riskScore.setRepresentative(true);
        riskScoreRepository.save(riskScore);
    }
}
