package com.consentradar.consentradar.api;

import com.consentradar.consentradar.api.dto.ConsentItemResponse;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.entity.User;
import com.consentradar.consentradar.entity.UserConsentCheck;
import com.consentradar.consentradar.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentApiServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ConsentItemRepository consentItemRepository;
    @Mock private UserConsentCheckRepository userConsentCheckRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private RiskScoreRepository riskScoreRepository;

    @InjectMocks
    private ConsentApiService consentApiService;

    private static final Long USER_ID = 1L;
    private static final Long COMPANY_ID = 10L;

    @Test
    void getConsentItems_returnsRequiredItemAlwaysChecked_evenWithNoUserCheckRecord() {
        ConsentItem required = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(required));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());

        List<ConsentItemResponse> result = consentApiService.getConsentItems(USER_ID, COMPANY_ID);

        assertEquals(1, result.size());
        assertTrue(result.get(0).isChecked());
    }

    @Test
    void getConsentItems_returnsOptionalItemChecked_onlyWhenUserActuallyCheckedIt() {
        ConsentItem checkedOptional = consentItem(1L, ConsentItem.ItemType.OPTIONAL);
        ConsentItem uncheckedOptional = consentItem(2L, ConsentItem.ItemType.OPTIONAL);
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID))
                .thenReturn(List.of(checkedOptional, uncheckedOptional));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(userConsentCheck(checkedOptional, true), userConsentCheck(uncheckedOptional, false)));

        List<ConsentItemResponse> result = consentApiService.getConsentItems(USER_ID, COMPANY_ID);

        assertEquals(2, result.size());
        assertTrue(result.stream().filter(r -> r.getConsentItemId().equals(1L)).findFirst().orElseThrow().isChecked());
        assertTrue(result.stream().filter(r -> r.getConsentItemId().equals(2L)).findFirst().orElseThrow().isChecked() == false);
    }

    @Test
    void getConsentItems_mapsAllFiveVariableScores() {
        ConsentItem item = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        item.setDsScore(5);
        item.setEsScore(3);
        item.setTfScore(2);
        item.setPcScore(1.5);
        item.setAiScore(1.0);
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(item));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());

        ConsentItemResponse response = consentApiService.getConsentItems(USER_ID, COMPANY_ID).get(0);

        assertEquals(5, response.getDsScore());
        assertEquals(3, response.getEsScore());
        assertEquals(2, response.getTfScore());
        assertEquals(1.5, response.getPcScore());
        assertEquals(1.0, response.getAiScore());
    }

    private ConsentItem consentItem(Long id, ConsentItem.ItemType type) {
        ConsentItem item = new ConsentItem();
        item.setConsentItemId(id);
        item.setItemType(type);
        item.setItemName("항목" + id);
        item.setDsScore(1);
        item.setEsScore(1);
        item.setTfScore(1);
        item.setPcScore(1.0);
        item.setAiScore(1.0);
        Company company = new Company();
        company.setCompanyId(COMPANY_ID);
        item.setCompany(company);
        return item;
    }

    private UserConsentCheck userConsentCheck(ConsentItem item, boolean checked) {
        UserConsentCheck check = new UserConsentCheck();
        User user = new User();
        check.setUser(user);
        check.setConsentItem(item);
        check.setChecked(checked);
        return check;
    }
}
