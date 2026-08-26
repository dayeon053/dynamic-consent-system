package com.consentradar.consentradar.consentitem;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.repository.ConsentItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentItemUpsertServiceTest {

    @Mock private ConsentItemRepository consentItemRepository;

    private ConsentItemUpsertService upsertService;

    private static final Long COMPANY_ID = 10L;

    @BeforeEach
    void setUp() {
        upsertService = new ConsentItemUpsertService(consentItemRepository);
    }

    @Test
    void upsert_setsActiveTrue_onNewlyInsertedItem() {
        Company company = company();
        when(consentItemRepository.findByCompany_CompanyIdAndItemName(COMPANY_ID, "새 항목"))
                .thenReturn(java.util.Optional.empty());
        when(consentItemRepository.save(org.mockito.ArgumentMatchers.any(ConsentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConsentItem saved = upsertService.upsert(company, "새 항목", ConsentItem.ItemType.REQUIRED,
                5, 2, 3, 1.0, 1.0);

        assertTrue(saved.isActive(), "새로 만든 항목은 active=true여야 한다");
    }

    /**
     * [TODO 확정 회귀 테스트] 예전에 소프트 삭제(active=false)됐던 항목이 이번 크롤링에서
     * 다시 매칭되면(같은 itemName), 다시 active=true로 되살아나야 한다.
     */
    @Test
    void upsert_reactivatesItem_whenPreviouslyDeactivatedItemReappears() {
        Company company = company();
        ConsentItem deactivated = existingItem("예전 항목");
        deactivated.setActive(false);
        when(consentItemRepository.findByCompany_CompanyIdAndItemName(COMPANY_ID, "예전 항목"))
                .thenReturn(java.util.Optional.of(deactivated));
        when(consentItemRepository.save(deactivated)).thenReturn(deactivated);

        ConsentItem result = upsertService.upsert(company, "예전 항목", ConsentItem.ItemType.OPTIONAL,
                3, 2, 3, 1.0, 1.0);

        assertTrue(result.isActive(), "재크롤링에서 다시 매칭된 항목은 active=true로 복구돼야 한다");
    }

    @Test
    void deactivateMissing_deactivatesOnlyItemsNotInMatchedSet() {
        Company company = company();
        ConsentItem stillMatched = existingItem("계속 있는 항목");
        ConsentItem noLongerMatched = existingItem("사라진 항목");
        when(consentItemRepository.findByCompany_CompanyIdAndActiveTrue(COMPANY_ID))
                .thenReturn(List.of(stillMatched, noLongerMatched));
        when(consentItemRepository.save(org.mockito.ArgumentMatchers.any(ConsentItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        upsertService.deactivateMissing(company, Set.of("계속 있는 항목"));

        assertTrue(stillMatched.isActive(), "이번 크롤링에도 매칭된 항목은 active로 유지돼야 한다");
        assertFalse(noLongerMatched.isActive(), "이번 크롤링에 없는 항목은 active=false로 바뀌어야 한다");

        ArgumentCaptor<ConsentItem> savedCaptor = ArgumentCaptor.forClass(ConsentItem.class);
        verify(consentItemRepository, times(1)).save(savedCaptor.capture());
        assertTrue(savedCaptor.getValue() == noLongerMatched,
                "save()는 비활성화 대상(사라진 항목)에 대해서만 호출돼야 한다");
    }

    @Test
    void deactivateMissing_doesNothing_whenAllActiveItemsAreStillMatched() {
        Company company = company();
        ConsentItem stillMatched = existingItem("계속 있는 항목");
        when(consentItemRepository.findByCompany_CompanyIdAndActiveTrue(COMPANY_ID))
                .thenReturn(List.of(stillMatched));

        upsertService.deactivateMissing(company, Set.of("계속 있는 항목", "이번에만 새로 생긴 항목"));

        assertTrue(stillMatched.isActive());
        verify(consentItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Company company() {
        Company company = new Company();
        company.setCompanyId(COMPANY_ID);
        return company;
    }

    private ConsentItem existingItem(String itemName) {
        ConsentItem item = new ConsentItem();
        item.setConsentItemId(1L);
        item.setCompany(company());
        item.setItemName(itemName);
        item.setItemType(ConsentItem.ItemType.OPTIONAL);
        item.setDsScore(1);
        item.setEsScore(1);
        item.setTfScore(1);
        item.setPcScore(1.0);
        item.setAiScore(1.0);
        item.setActive(true);
        return item;
    }
}
