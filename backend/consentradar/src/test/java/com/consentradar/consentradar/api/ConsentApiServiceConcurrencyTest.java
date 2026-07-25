package com.consentradar.consentradar.api;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.entity.User;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.ConsentItemRepository;
import com.consentradar.consentradar.repository.RiskScoreRepository;
import com.consentradar.consentradar.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sprint04 황다연 — 엣지케이스 검증: 같은 기업의 선택동의 여러 개를 동시에 체크/해제할 때
 * 위험도 대표 RiskScore row가 경합으로 중복 생성되거나(유니크 제약 위반) 두 토글 중
 * 하나가 소실(lost update)되지 않는지 실제 DB(H2, MySQL 호환 모드)로 검증한다.
 *
 * ConsentApiService.toggleConsent 문서에 정리한 시나리오를 재현한다: 오늘자 대표 row가
 * 아직 없는 상태에서 서로 다른 선택동의 항목 두 개를 정확히 같은 순간에 토글하면, 재시도
 * 로직(ConcurrentUpdateRetrier)이 없을 때는 유니크 제약(user_id, company_id, scored_at,
 * is_representative) 위반으로 둘 중 하나가 실패한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConsentApiServiceConcurrencyTest {

    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private ConsentItemRepository consentItemRepository;
    @Autowired private RiskScoreRepository riskScoreRepository;
    @Autowired private ConsentApiService consentApiService;
    @Autowired private ConcurrentUpdateRetrier retrier;

    private Long userId;
    private Long companyId;
    private Long optionalItemAId;
    private Long optionalItemBId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("concurrency-test-" + System.nanoTime() + "@example.com");
        user.setPassword("test-password");
        user.setNickname("동시성테스트유저");
        userId = userRepository.save(user).getUserId();

        Company company = new Company();
        company.setCompanyName("동시성테스트기업-" + System.nanoTime());
        company.setPackageName("test.concurrency." + System.nanoTime());
        company.setPrivacyUrl("https://example.com/privacy");
        company.setIsmsCertified(false);
        companyId = companyRepository.save(company).getCompanyId();

        ConsentItem required = newItem(company, ConsentItem.ItemType.REQUIRED, "필수항목", 3, 1, 1, 1.0, 1.0);
        ConsentItem optionalA = newItem(company, ConsentItem.ItemType.OPTIONAL, "선택항목A", 5, 3, 3, 1.5, 1.5);
        ConsentItem optionalB = newItem(company, ConsentItem.ItemType.OPTIONAL, "선택항목B", 3, 2, 2, 1.0, 1.0);
        consentItemRepository.save(required);
        optionalItemAId = consentItemRepository.save(optionalA).getConsentItemId();
        optionalItemBId = consentItemRepository.save(optionalB).getConsentItemId();
    }

    /**
     * 타이밍에 따라 재현 여부가 갈릴 수 있어 여러 번 반복 실행해 신뢰도를 높인다.
     */
    @RepeatedTest(5)
    void togglingTwoDifferentOptionalItemsConcurrently_doesNotDuplicateRepresentativeRowOrThrow() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Void> toggleA = () -> {
            readyLatch.countDown();
            startLatch.await();
            retrier.retry(() -> consentApiService.toggleConsent(userId, optionalItemAId));
            return null;
        };
        Callable<Void> toggleB = () -> {
            readyLatch.countDown();
            startLatch.await();
            retrier.retry(() -> consentApiService.toggleConsent(userId, optionalItemBId));
            return null;
        };

        try {
            Future<Void> futureA = executor.submit(toggleA);
            Future<Void> futureB = executor.submit(toggleB);

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown(); // 두 토글을 최대한 같은 순간에 시작시켜 경합을 유도한다

            // get()이 예외 없이 끝나야 한다 — 유니크 제약/낙관적 락 경합이 재시도로 흡수됐다는 뜻.
            // (재시도 로직이 없었다면 이 중 하나가 여기서 예외를 던진다.)
            assertDoesNotThrow(() -> futureA.get(10, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> futureB.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        long repRowCount = riskScoreRepository
                .countByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                        userId, companyId, LocalDate.now());

        assertEquals(1, repRowCount,
                "동시 토글 후에도 (user, company, 오늘) 대표 row는 정확히 1건이어야 한다 (유니크 제약 위반으로 인한 중복 없이)");
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
