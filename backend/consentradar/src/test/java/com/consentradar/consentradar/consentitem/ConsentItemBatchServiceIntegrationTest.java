package com.consentradar.consentradar.consentitem;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.ConsentItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ConsentItemBatchService.saveAll()의 트랜잭션 롤백을 실제 로컬 MySQL(consentradar DB)에
 * 연결해 검증하는 통합 테스트. 테스트 전용 Company를 만들어 사용하고, 각 테스트가 끝나면
 * 해당 Company와 하위 ConsentItem을 정리해 다른 테스트/기존 데이터에 영향을 주지 않는다.
 * H2로 대체하지 않고 의도적으로 실 MySQL에 연결하므로, 로컬에 MySQL(consentradar DB)이
 * 떠 있어야 통과한다. 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@Tag("integration")
@SpringBootTest
class ConsentItemBatchServiceIntegrationTest {

    private static final String TEST_PACKAGE_NAME = "test.consentitem.rollback.integration";

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ConsentItemRepository consentItemRepository;

    @Autowired
    private ConsentItemBatchService consentItemBatchService;

    private Long testCompanyId;

    @BeforeEach
    void setUp() {
        // 이전 실행이 비정상 종료되어 남은 데이터가 있으면 먼저 정리한다.
        companyRepository.findByPackageName(TEST_PACKAGE_NAME)
                .ifPresent(this::deleteCompanyWithConsentItems);

        Company company = new Company();
        company.setCompanyName("트랜잭션롤백테스트기업");
        company.setPackageName(TEST_PACKAGE_NAME);
        company.setPrivacyUrl("https://example.com/privacy");
        company.setIsmsCertified(false);
        testCompanyId = companyRepository.save(company).getCompanyId();
    }

    @AfterEach
    void tearDown() {
        companyRepository.findById(testCompanyId)
                .ifPresent(this::deleteCompanyWithConsentItems);
    }

    private void deleteCompanyWithConsentItems(Company company) {
        consentItemRepository.deleteAll(consentItemRepository.findByCompany_CompanyId(company.getCompanyId()));
        companyRepository.delete(company);
    }

    @Test
    void saveAll_persistsAllItemsToRealDatabase_whenScoresAreValid() {
        List<ConsentItemDto> dtos = List.of(
                new ConsentItemDto(ConsentItem.ItemType.REQUIRED, "이름 수집", 5, 3, 2, 1.0, 1.0),
                new ConsentItemDto(ConsentItem.ItemType.OPTIONAL, "마케팅 수신 동의", 8, 6, 3, 1.5, 1.5),
                new ConsentItemDto(ConsentItem.ItemType.REQUIRED, "위치정보 수집", 7, 5, 4, 1.0, 1.5)
        );

        List<ConsentItem> saved = consentItemBatchService.saveAll(testCompanyId, dtos);

        assertEquals(3, saved.size());
        long countInDb = consentItemRepository.countByCompany_CompanyId(testCompanyId);
        assertEquals(3, countInDb, "3건 모두 실제 DB에 저장되어야 한다");
    }

    @Test
    void saveAll_rollsBackAllItems_whenLastItemHasOutOfRangeScore() {
        List<ConsentItemDto> dtos = List.of(
                new ConsentItemDto(ConsentItem.ItemType.REQUIRED, "정상 항목1", 5, 3, 2, 1.0, 1.0),
                new ConsentItemDto(ConsentItem.ItemType.OPTIONAL, "정상 항목2", 8, 6, 3, 1.5, 1.5),
                new ConsentItemDto(ConsentItem.ItemType.OPTIONAL, "범위 위반 항목", 11, 3, 2, 1.0, 1.0) // dsScore=11 > 10
        );

        assertThrows(InvalidConsentItemScoreException.class,
                () -> consentItemBatchService.saveAll(testCompanyId, dtos));

        long countInDb = consentItemRepository.countByCompany_CompanyId(testCompanyId);
        assertEquals(0, countInDb, "하나라도 범위를 벗어나면 앞선 항목까지 포함해 전체 롤백되어 DB에 0건이어야 한다");
    }

    @Test
    void saveAll_rollsBack_whenCompanyIdDoesNotExist() {
        long nonExistentCompanyId = findNonExistentCompanyId();

        List<ConsentItemDto> dtos = List.of(
                new ConsentItemDto(ConsentItem.ItemType.REQUIRED, "정상 항목", 5, 3, 2, 1.0, 1.0)
        );

        assertThrows(IllegalArgumentException.class,
                () -> consentItemBatchService.saveAll(nonExistentCompanyId, dtos));

        long countInDb = consentItemRepository.countByCompany_CompanyId(nonExistentCompanyId);
        assertEquals(0, countInDb, "존재하지 않는 companyId면 아무것도 저장되면 안 된다");
    }

    @Test
    void saveAll_rollsBackAlreadyInsertedItems_whenLaterItemViolatesDbConstraint() {
        // 애플리케이션 레벨 검증(validateScoreRange)은 itemName의 null 여부를 확인하지 않으므로
        // 이 DTO는 검증을 통과하고 실제 INSERT 시점에 DB의 NOT NULL 제약(item_name)을 위반한다.
        // ConsentItem은 GenerationType.IDENTITY라서 saveAll() 안에서 항목마다 즉시 INSERT가
        // 실행되므로, 1·2번째 항목은 이미 INSERT가 나간 뒤 3번째에서 실패 -> 트랜잭션 전체 롤백을
        // 진짜로 재현한다.
        List<ConsentItemDto> dtos = List.of(
                new ConsentItemDto(ConsentItem.ItemType.REQUIRED, "정상 항목1", 5, 3, 2, 1.0, 1.0),
                new ConsentItemDto(ConsentItem.ItemType.OPTIONAL, "정상 항목2", 8, 6, 3, 1.5, 1.5),
                new ConsentItemDto(ConsentItem.ItemType.OPTIONAL, null, 4, 3, 2, 1.0, 1.0) // itemName=null -> NOT NULL 위반
        );

        assertThrows(DataIntegrityViolationException.class,
                () -> consentItemBatchService.saveAll(testCompanyId, dtos));

        long countInDb = consentItemRepository.countByCompany_CompanyId(testCompanyId);
        assertEquals(0, countInDb,
                "1·2번째 항목이 이미 INSERT된 뒤 3번째에서 DB 제약 위반이 나도 트랜잭션 전체가 롤백되어 0건이어야 한다");
    }

    private long findNonExistentCompanyId() {
        long candidate = 999_000L;
        while (companyRepository.existsById(candidate)) {
            candidate++;
        }
        return candidate;
    }
}