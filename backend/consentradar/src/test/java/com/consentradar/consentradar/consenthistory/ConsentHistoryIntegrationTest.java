package com.consentradar.consentradar.consenthistory;

import com.consentradar.consentradar.api.ConsentApiService;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 동의 변경 이력 조회(GET /users/{userId}/consents/history) end-to-end 검증.
 * ConsentApiService.toggleConsent()를 통해 실제로 이력이 남고, 조회 시 변경 시각
 * 오름차순으로 노출되는지 실제 로컬 MySQL(consentradar DB)로 확인한다. 기본
 * `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@Tag("integration")
@SpringBootTest
class ConsentHistoryIntegrationTest {

    private static final String TEST_EMAIL = "test.consenthistory.integration@example.com";
    private static final String TEST_PACKAGE_NAME = "test.consenthistory.integration";

    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private ConsentItemRepository consentItemRepository;
    @Autowired private UserConsentCheckRepository userConsentCheckRepository;
    @Autowired private UserConsentHistoryRepository userConsentHistoryRepository;
    @Autowired private RiskScoreRepository riskScoreRepository;
    @Autowired private ConsentApiService consentApiService;
    @Autowired private UserConsentHistoryService userConsentHistoryService;

    private Long userId;
    private Long optionalItemId;

    @BeforeEach
    void setUp() {
        cleanUp();

        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setPassword("test-password");
        user.setNickname("동의이력테스트유저");
        userId = userRepository.save(user).getUserId();

        Company company = new Company();
        company.setCompanyName("동의이력테스트기업");
        company.setLegalName("동의이력테스트기업");
        company.setCategory("기타");
        company.setPackageName(TEST_PACKAGE_NAME);
        company.setPrivacyUrl("https://example.com/privacy");
        company.setIsmsCertified(false);
        Company savedCompany = companyRepository.save(company);

        ConsentItem required = newItem(savedCompany, ConsentItem.ItemType.REQUIRED, "필수항목", 1, 1, 1, 1.0, 1.0);
        ConsentItem optional = newItem(savedCompany, ConsentItem.ItemType.OPTIONAL, "선택항목", 3, 2, 2, 1.0, 1.0);
        consentItemRepository.save(required);
        optionalItemId = consentItemRepository.save(optional).getConsentItemId();
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
                riskScoreRepository.findByCompany_CompanyId(company.getCompanyId())
                        .forEach(riskScoreRepository::delete);
            });
            userRepository.delete(user);
        });
        companyRepository.findByPackageName(TEST_PACKAGE_NAME).ifPresent(companyRepository::delete);
    }

    @Test
    void togglingConsent_recordsHistory_andGetHistoryReturnsInChronologicalOrder() throws InterruptedException {
        consentApiService.toggleConsent(userId, optionalItemId, null); // 미체크 -> 체크
        Thread.sleep(20);
        consentApiService.toggleConsent(userId, optionalItemId, null); // 체크 -> 미체크
        Thread.sleep(20);
        consentApiService.toggleConsent(userId, optionalItemId, null); // 미체크 -> 체크

        List<UserConsentHistoryItemDto> history = userConsentHistoryService.getHistory(userId);

        assertEquals(3, history.size(), "토글 3회만큼 이력이 3건 쌓여야 한다");
        assertTrue(!history.get(0).changedAt().isAfter(history.get(1).changedAt())
                        && !history.get(1).changedAt().isAfter(history.get(2).changedAt()),
                "changed_at 기준 오름차순(시간순)으로 정렬돼야 한다");
        assertTrue(history.get(0).isChecked(), "1번째 토글 후에는 체크 상태였어야 한다");
        assertFalse(history.get(1).isChecked(), "2번째 토글 후에는 미체크 상태였어야 한다");
        assertTrue(history.get(2).isChecked(), "3번째 토글 후에는 다시 체크 상태였어야 한다");

        UserConsentHistoryItemDto first = history.get(0);
        assertEquals(optionalItemId, first.consentItemId());
        assertEquals("선택항목", first.itemName());
    }

    @Test
    void getHistory_returnsEmptyList_whenUserHasNoConsentChangesYet() {
        List<UserConsentHistoryItemDto> history = userConsentHistoryService.getHistory(userId);

        assertTrue(history.isEmpty());
    }

    private ConsentItem newItem(Company company, ConsentItem.ItemType type, String name,
                                 int ds, int es, int tf, double pc, double ai) {
        ConsentItem item = new ConsentItem();
        item.setCompany(company);
        item.setItemType(type);
        item.setItemName(name);
        item.setDsScore(ds);
        item.setEsScore(es);
        item.setTfScore(tf);
        item.setPcScore(pc);
        item.setAiScore(ai);
        return item;
    }
}