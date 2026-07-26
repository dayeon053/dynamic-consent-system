package com.consentradar.consentradar.api;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.repository.ConsentItemRepository;
import com.consentradar.consentradar.repository.UserConsentCheckRepository;
import com.dynamicconsent.model.RiskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalRiskCalculatorTest {

    @Mock private ConsentItemRepository consentItemRepository;
    @Mock private UserConsentCheckRepository userConsentCheckRepository;

    private PersonalRiskCalculator calculator;

    private static final Long USER_ID = 1L;
    private static final Long COMPANY_ID = 10L;

    @BeforeEach
    void setUp() {
        calculator = new PersonalRiskCalculator(consentItemRepository, userConsentCheckRepository);
    }

    /**
     * [수정 이력 — default 뭉개기 버그 검증]
     * DS/ES/TF 점수→등급 매핑은 유효값만 명시적으로 처리하고, 그 외 값(DB 오염 등)은
     * 조용히 HIGH/최댓값으로 잘못 매핑되지 않고 예외로 드러나야 한다.
     */
    @Test
    void calculate_throwsIllegalArgumentException_whenDsScoreIsInvalid() {
        ConsentItem item = item(1, 1, 1, 1.0, 1.0);
        item.setDsScore(2); // 유효값 아님 (1, 3, 5만 허용)
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(item));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(USER_ID, COMPANY_ID));
    }

    @Test
    void calculate_throwsIllegalArgumentException_whenEsScoreIsInvalid() {
        ConsentItem item = item(1, 1, 1, 1.0, 1.0);
        item.setEsScore(0); // 유효값 아님 (1, 2, 3만 허용)
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(item));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(USER_ID, COMPANY_ID));
    }

    @Test
    void calculate_throwsIllegalArgumentException_whenTfScoreIsInvalid() {
        ConsentItem item = item(1, 1, 4, 1.0, 1.0); // 유효값 아님 (1, 2, 3만 허용)
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(item));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(USER_ID, COMPANY_ID));
    }

    @Test
    void calculate_mapsAllValidDsScoresCorrectly() {
        // DS=1,3,5 각각 유효값이라 예외 없이 정상 계산돼야 한다.
        for (int ds : List.of(1, 3, 5)) {
            ConsentItem item = item(ds, 1, 1, 1.0, 1.0);
            when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(item));
            when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                    .thenReturn(List.of());

            RiskResult result = calculator.calculate(USER_ID, COMPANY_ID);

            assertEquals(0, BigDecimal.valueOf(ds + 2.0).compareTo(BigDecimal.valueOf(result.getScore())),
                    "DS=" + ds + "일 때 점수는 " + ds + " + (1*1*1*1)*2 = " + (ds + 2.0) + "이어야 한다");
        }
    }

    private ConsentItem item(int ds, int es, int tf, double pc, double ai) {
        ConsentItem item = new ConsentItem();
        item.setConsentItemId(1L);
        item.setItemType(ConsentItem.ItemType.REQUIRED);
        item.setItemName("항목");
        item.setDsScore(ds);
        item.setEsScore(es);
        item.setTfScore(tf);
        item.setPcScore(pc);
        item.setAiScore(ai);
        Company company = new Company();
        company.setCompanyId(COMPANY_ID);
        item.setCompany(company);
        return item;
    }
}
