package com.consentradar.consentradar.consentitem;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentItemBatchServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private ConsentItemUpsertService consentItemUpsertService;

    @Test
    void saveAll_upsertsEveryItemWhenScoresAreValid() {
        Company company = new Company();
        company.setCompanyId(1L);
        company.setCompanyName("테스트기업");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(consentItemUpsertService.upsert(any(), anyString(), any(), anyInt(), anyInt(), anyInt(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    ConsentItem item = new ConsentItem();
                    item.setCompany(invocation.getArgument(0));
                    item.setItemName(invocation.getArgument(1));
                    return item;
                });

        List<ConsentItemDto> dtos = List.of(
                new ConsentItemDto(ConsentItem.ItemType.REQUIRED, "이름 수집", 5, 3, 2, 1.0, 1.0),
                new ConsentItemDto(ConsentItem.ItemType.OPTIONAL, "마케팅 수신 동의", 8, 6, 3, 1.5, 1.5)
        );

        ConsentItemBatchService service =
                new ConsentItemBatchService(companyRepository, consentItemUpsertService);

        List<ConsentItem> saved = service.saveAll(1L, dtos);

        assertEquals(2, saved.size());
        verify(consentItemUpsertService, times(1))
                .upsert(company, "이름 수집", ConsentItem.ItemType.REQUIRED, 5, 3, 2, 1.0, 1.0);
        verify(consentItemUpsertService, times(1))
                .upsert(company, "마케팅 수신 동의", ConsentItem.ItemType.OPTIONAL, 8, 6, 3, 1.5, 1.5);
    }

    @Test
    void saveAll_throwsAndDoesNotPersistWhenAnyScoreOutOfRange() {
        Company company = new Company();
        company.setCompanyId(1L);
        company.setCompanyName("테스트기업");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));

        List<ConsentItemDto> dtos = List.of(
                new ConsentItemDto(ConsentItem.ItemType.REQUIRED, "정상 항목", 5, 3, 2, 1.0, 1.0),
                new ConsentItemDto(ConsentItem.ItemType.OPTIONAL, "범위 밖 항목", 11, 3, 2, 1.0, 1.0)
        );

        ConsentItemBatchService service =
                new ConsentItemBatchService(companyRepository, consentItemUpsertService);

        assertThrows(InvalidConsentItemScoreException.class, () -> service.saveAll(1L, dtos));
        verifyNoInteractions(consentItemUpsertService);
    }

    @Test
    void saveAll_throwsWhenCompanyNotFound() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        ConsentItemBatchService service =
                new ConsentItemBatchService(companyRepository, consentItemUpsertService);

        assertThrows(IllegalArgumentException.class, () -> service.saveAll(99L, List.of()));
        verifyNoInteractions(consentItemUpsertService);
    }
}