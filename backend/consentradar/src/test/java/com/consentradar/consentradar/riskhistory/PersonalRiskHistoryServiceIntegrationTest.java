package com.consentradar.consentradar.riskhistory;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.RiskScore;
import com.consentradar.consentradar.entity.User;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.RiskScoreRepository;
import com.consentradar.consentradar.repository.UserRepository;
import com.dynamicconsent.model.RiskGrade;
import com.dynamicconsent.model.RiskResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PersonalRiskHistoryService의 append-only dedup 및 히스토리 조회를 실제 로컬
 * MySQL(consentradar DB)에 연결해 검증하는 통합 테스트. 테스트 전용 User/Company를 만들어
 * 사용하고, 각 테스트가 끝나면 해당 RiskScore/User/Company를 정리한다. 기본 `./gradlew test`
 * 에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@Tag("integration")
@SpringBootTest
class PersonalRiskHistoryServiceIntegrationTest {

    private static final String TEST_EMAIL = "test.riskhistory.integration@example.com";
    private static final String TEST_PACKAGE_NAME = "test.riskhistory.integration";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private PersonalRiskHistoryService personalRiskHistoryService;

    private Long testUserId;
    private Long testCompanyId;

    @BeforeEach
    void setUp() {
        cleanUp();

        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setPassword("test-password");
        user.setNickname("리스크히스토리테스트유저");
        testUserId = userRepository.save(user).getUserId();

        Company company = new Company();
        company.setCompanyName("리스크히스토리테스트기업");
        company.setLegalName("리스크히스토리테스트기업");
        company.setCategory("기타");
        company.setPackageName(TEST_PACKAGE_NAME);
        company.setPrivacyUrl("https://example.com/privacy");
        company.setIsmsCertified(false);
        testCompanyId = companyRepository.save(company).getCompanyId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(user -> {
            riskScoreRepository.findByUser_UserIdAndCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtAsc(
                    user.getUserId(), companyRepository.findByPackageName(TEST_PACKAGE_NAME)
                            .map(Company::getCompanyId).orElse(-1L))
                    .forEach(riskScoreRepository::delete);
            userRepository.delete(user);
        });
        companyRepository.findByPackageName(TEST_PACKAGE_NAME).ifPresent(companyRepository::delete);
    }

    @Test
    void saveIfAbsent_persistsToRealDatabase_onFirstCallOfTheDay() {
        User user = userRepository.findById(testUserId).orElseThrow();
        Company company = companyRepository.findById(testCompanyId).orElseThrow();
        RiskResult result = new RiskResult(18.0, RiskGrade.MEDIUM);

        Optional<RiskScore> saved = personalRiskHistoryService.saveIfAbsent(user, company, result);

        assertTrue(saved.isPresent());
        List<RiskScore> historyRows = riskScoreRepository
                .findByUser_UserIdAndCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtAsc(
                        testUserId, testCompanyId);
        assertEquals(1, historyRows.size(), "오늘 첫 저장이면 실제 DB에 1건 있어야 한다");
        assertEquals(LocalDate.now(), historyRows.get(0).getScoredAt());
    }

    @Test
    void saveIfAbsent_doesNotInsertSecondRow_whenCalledTwiceOnSameDay() {
        User user = userRepository.findById(testUserId).orElseThrow();
        Company company = companyRepository.findById(testCompanyId).orElseThrow();

        Optional<RiskScore> first = personalRiskHistoryService
                .saveIfAbsent(user, company, new RiskResult(18.0, RiskGrade.MEDIUM));
        Optional<RiskScore> second = personalRiskHistoryService
                .saveIfAbsent(user, company, new RiskResult(30.0, RiskGrade.HIGH));

        assertTrue(first.isPresent());
        assertTrue(second.isEmpty(), "같은 날 두 번째 호출은 저장을 건너뛰어야 한다");

        List<RiskScore> historyRows = riskScoreRepository
                .findByUser_UserIdAndCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtAsc(
                        testUserId, testCompanyId);
        assertEquals(1, historyRows.size(), "같은 날 중복 저장 없이 1건만 남아야 한다(append-only dedup)");
        assertEquals(0, BigDecimal.valueOf(18.0).compareTo(historyRows.get(0).getTotalScore()),
                "먼저 저장된 값이 유지되어야 한다 (두 번째 호출은 기존 row를 덮어쓰지 않음)");
    }

    @Test
    void getHistory_returnsMultiDayHistoryOrderedByDate() {
        // append-only 특성상 서로 다른 날짜 row는 직접 저장해 히스토리를 시뮬레이션한다.
        saveHistoricalRow(LocalDate.now().minusDays(2), 10.0, RiskScore.Grade.LOW);
        saveHistoricalRow(LocalDate.now().minusDays(1), 20.0, RiskScore.Grade.MEDIUM);
        saveHistoricalRow(LocalDate.now(), 30.0, RiskScore.Grade.HIGH);

        List<RiskScoreHistoryItemDto> history =
                personalRiskHistoryService.getHistory(testUserId, testCompanyId);

        assertEquals(3, history.size());
        assertEquals(LocalDate.now().minusDays(2), history.get(0).scoredAt());
        assertEquals(LocalDate.now().minusDays(1), history.get(1).scoredAt());
        assertEquals(LocalDate.now(), history.get(2).scoredAt());
    }

    @Test
    void uniqueConstraint_doesNotBlockBatchRowsWithNullUser_onSameCompanyAndDate() {
        // 배치 파이프라인(RiskPipelineService)은 user=null로 같은 회사+날짜에 여러 row를
        // 저장한다. MySQL은 NULL을 서로 다른 값으로 취급하므로 이 유니크 제약에 걸리지
        // 않아야 한다 — 배치 흐름을 건드리지 않는다는 설계 전제를 실제 DB로 검증한다.
        Company company = companyRepository.findById(testCompanyId).orElseThrow();

        RiskScore batchRow1 = newBatchRiskScore(company, RiskScore.Grade.LOW, false);
        RiskScore batchRow2 = newBatchRiskScore(company, RiskScore.Grade.MEDIUM, false);
        RiskScore batchRepRow = newBatchRiskScore(company, RiskScore.Grade.MEDIUM, true);

        assertDoesNotThrow(() -> {
            riskScoreRepository.save(batchRow1);
            riskScoreRepository.save(batchRow2);
            riskScoreRepository.save(batchRepRow);
        });
    }

    private RiskScore newBatchRiskScore(Company company, RiskScore.Grade grade, boolean representative) {
        RiskScore riskScore = new RiskScore();
        riskScore.setUser(null);
        riskScore.setCompany(company);
        riskScore.setTotalScore(BigDecimal.valueOf(10.0));
        riskScore.setGrade(grade);
        riskScore.setScoredAt(LocalDate.now());
        riskScore.setRepresentative(representative);
        return riskScore;
    }

    private void saveHistoricalRow(LocalDate scoredAt, double score, RiskScore.Grade grade) {
        User user = userRepository.findById(testUserId).orElseThrow();
        Company company = companyRepository.findById(testCompanyId).orElseThrow();

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
