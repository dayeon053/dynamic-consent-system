package com.consentradar.consentradar.repository;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 한글이 들어가는 컬럼(company_name, consent_item.item_name)이 실제 MySQL에 저장됐다가
 * 다시 조회됐을 때 바이트 단위로 원본과 일치하는지 검증하는 왕복(round-trip) 테스트.
 *
 * [배경 — 2026-08-26] "GET /companies의 한글 회사명이 깨져 보인다"는 의심을 받았지만,
 * 실제로는 DB에 처음부터 정상적인 UTF-8로 저장돼 있었고 mysql CLI를
 * --default-character-set=utf8mb4 없이 접속했을 때만(그 세션이 latin1로 잡혀서) 표시가
 * 깨지는 클라이언트 쪽 문제였음이 HEX() 바이트 비교로 확인됐다. 문자열 {@code .equals()}
 * 비교만으로는 "이미 깨진 값을 저장하고 다시 깨진 값을 읽으면 Java String 레벨에서는
 * 일치로 보이는" 이중 인코딩 케이스를 놓칠 수 있어, 이 테스트는 문자열 비교뿐 아니라
 * {@code getBytes(UTF_8)} 바이트 비교까지 한다.
 *
 * H2(MODE=MySQL, {@code application-test.yml})는 실제 MySQL의 charset/collation 설정을
 * 재현하지 않으므로, 반드시 실제 로컬 MySQL을 쓰는 이 integration 테스트에서 검증해야
 * 의미가 있다({@code @Tag("integration")} — 기본 {@code ./gradlew test}에서는 제외되고
 * {@code ./gradlew integrationTest}로만 실행된다).
 *
 * 테스트 메서드를 {@code @Transactional}로 감싸 테스트가 끝나면 자동 롤백되게 한다 —
 * 별도 정리(cleanup) 코드 없이도 실제 MySQL에 흔적을 남기지 않는다. 롤백 전에
 * {@link EntityManager#flush()}로 실제 INSERT를 DB에 반영하고 {@link EntityManager#clear()}로
 * 영속성 컨텍스트를 비워, 이후 조회가 JPA 1차 캐시가 아니라 진짜 MySQL 왕복을 거치도록 한다.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class HangulRoundTripIntegrationTest {

    private static final String TEST_PACKAGE_NAME = "test.encoding.roundtrip.integration";
    private static final String COMPANY_NAME = "카카오테스트기업";
    private static final String LEGAL_NAME = "(주)한글왕복검증";
    private static final String CATEGORY = "SNS·금융복합";
    private static final String ITEM_NAME = "개인위치정보 및 행태정보 수집·이용 동의";

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ConsentItemRepository consentItemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void koreanCompanyNameAndItemName_matchOriginalByteForByte_afterRoundTripThroughMysql() {
        Company company = new Company();
        company.setCompanyName(COMPANY_NAME);
        company.setLegalName(LEGAL_NAME);
        company.setCategory(CATEGORY);
        company.setPackageName(TEST_PACKAGE_NAME);
        company.setPrivacyUrl("https://example.com/privacy");
        company.setIsmsCertified(false);
        Long testCompanyId = companyRepository.save(company).getCompanyId();

        ConsentItem item = new ConsentItem();
        item.setCompany(company);
        item.setItemName(ITEM_NAME);
        item.setItemType(ConsentItem.ItemType.OPTIONAL);
        item.setDsScore(3);
        item.setEsScore(2);
        item.setTfScore(3);
        item.setPcScore(1.0);
        item.setAiScore(1.0);
        Long itemId = consentItemRepository.save(item).getConsentItemId();

        // JPA 1차 캐시에서 그대로 돌려받는 게 아니라 실제로 MySQL을 다시 왕복하도록
        // 영속성 컨텍스트를 비운다.
        entityManager.flush();
        entityManager.clear();

        Company reloadedCompany = companyRepository.findById(testCompanyId).orElseThrow();
        ConsentItem reloadedItem = consentItemRepository.findById(itemId).orElseThrow();

        // 문자열 비교
        assertEquals(COMPANY_NAME, reloadedCompany.getCompanyName());
        assertEquals(LEGAL_NAME, reloadedCompany.getLegalName());
        assertEquals(CATEGORY, reloadedCompany.getCategory());
        assertEquals(ITEM_NAME, reloadedItem.getItemName());

        // 바이트 비교 — 이중 인코딩(mojibake round-trip)까지 잡아내기 위해 문자열 비교만으론
        // 부족하다. 저장 전 원본 문자열을 UTF-8 바이트로 인코딩한 것과, DB를 왕복해 다시 읽은
        // 문자열을 UTF-8 바이트로 인코딩한 것이 완전히 같아야 한다.
        assertArrayEquals(COMPANY_NAME.getBytes(StandardCharsets.UTF_8),
                reloadedCompany.getCompanyName().getBytes(StandardCharsets.UTF_8),
                "company_name이 DB 왕복 후 바이트 단위로 원본과 달라졌다");
        assertArrayEquals(LEGAL_NAME.getBytes(StandardCharsets.UTF_8),
                reloadedCompany.getLegalName().getBytes(StandardCharsets.UTF_8),
                "legal_name이 DB 왕복 후 바이트 단위로 원본과 달라졌다");
        assertArrayEquals(ITEM_NAME.getBytes(StandardCharsets.UTF_8),
                reloadedItem.getItemName().getBytes(StandardCharsets.UTF_8),
                "consent_item.item_name이 DB 왕복 후 바이트 단위로 원본과 달라졌다");
    }
}
