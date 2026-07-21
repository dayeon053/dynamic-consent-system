package com.consentradar.consentradar.consentitem;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.ConsentItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentItemBatchServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private ConsentItemRepository consentItemRepository;

    @Test
    void saveAll_savesAllItemsInOneBatchWhenScoresAreValid() {
        Company company = new Company();
        company.setCompanyId(1L);
        company.setCompanyName("테스트기업");
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(consentItemRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ConsentItemDto> dtos = List.of(
                new ConsentItemDto(ConsentItem.ItemType.REQUIRED, "이름 수집", 5, 3, 2, 1.0, 1.0),
                new ConsentItemDto(ConsentItem.ItemType.OPTIONAL, "마케팅 수신 동의", 8, 6, 3, 1.5, 1.5)
        );

        ConsentItemBatchService service =
                new ConsentItemBatchService(companyRepository, consentItemRepository);

        List<ConsentItem> saved = service.saveAll(1L, dtos);

        assertEquals(2, saved.size());
        verify(consentItemRepository, times(1)).saveAll(anyList());
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
                new ConsentItemBatchService(companyRepository, consentItemRepository);

        assertThrows(InvalidConsentItemScoreException.class, () -> service.saveAll(1L, dtos));
        verify(consentItemRepository, never()).saveAll(anyList());
    }

    @Test
    void saveAll_throwsWhenCompanyNotFound() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        ConsentItemBatchService service =
                new ConsentItemBatchService(companyRepository, consentItemRepository);

        assertThrows(IllegalArgumentException.class, () -> service.saveAll(99L, List.of()));
        verify(consentItemRepository, never()).saveAll(anyList());
    }
}