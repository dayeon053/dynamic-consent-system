package com.consentradar.consentradar.notice;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A-3 (api_spec_v2_final.md 확정 사항 1번): GET /notices가 isChanged=true인 스냅샷만
 * 반환하는지 실제 로컬 MySQL(consentradar DB)로 확인한다. 기본 `./gradlew test`에서는
 * 제외되고 `./gradlew integrationTest`로만 실행된다(다른 *IntegrationTest와 동일 관례).
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class NoticeControllerIntegrationTest {

    private static final String TEST_PACKAGE_NAME = "test.notice.integration";

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PolicySnapshotRepository policySnapshotRepository;

    private Long companyId;

    @BeforeEach
    void setUp() {
        cleanUp();

        Company company = new Company();
        company.setCompanyName("공지필터테스트기업");
        company.setLegalName("공지필터테스트기업");
        company.setCategory("기타");
        company.setPackageName(TEST_PACKAGE_NAME);
        company.setPrivacyUrl("https://example.com/privacy");
        company.setIsmsCertified(false);
        companyId = companyRepository.save(company).getCompanyId();

        saveSnapshot(company, "원문1", false); // 변경 없음 — 응답에서 제외돼야 한다
        saveSnapshot(company, "원문2(변경)", true); // 변경 있음 — 응답에 포함돼야 한다
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        companyRepository.findByPackageName(TEST_PACKAGE_NAME).ifPresent(company -> {
            policySnapshotRepository.findByCompany_CompanyId(company.getCompanyId())
                    .forEach(policySnapshotRepository::delete);
            companyRepository.delete(company);
        });
    }

    private void saveSnapshot(Company company, String rawText, boolean changed) {
        PolicySnapshot snapshot = new PolicySnapshot();
        snapshot.setCompany(company);
        snapshot.setRawText(rawText);
        snapshot.setChanged(changed);
        policySnapshotRepository.save(snapshot);
    }

    @Test
    void getNotices_excludesUnchangedSnapshots_andIncludesOnlyChangedOnes() throws Exception {
        mockMvc.perform(get("/notices").param("page", "0").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.companyId == " + companyId + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.companyId == " + companyId + ")].isChanged", everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    void getNotices_returnsExactlyOneEntry_forThisCompany() throws Exception {
        // 이 기업에 스냅샷을 2건(변경 없음 1 + 변경 있음 1) 심었지만, 필터링 후에는
        // 변경 있음 1건만 남아야 한다.
        long changedCountForThisCompany = policySnapshotRepository
                .findByCompany_CompanyId(companyId).stream()
                .filter(PolicySnapshot::isChanged)
                .count();
        assertTrue(changedCountForThisCompany == 1,
                "테스트 전제 확인: 이 기업엔 변경 스냅샷이 정확히 1건이어야 한다");

        mockMvc.perform(get("/notices").param("page", "0").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.companyId == " + companyId + ")]").value(hasSize(1)));
    }
}
