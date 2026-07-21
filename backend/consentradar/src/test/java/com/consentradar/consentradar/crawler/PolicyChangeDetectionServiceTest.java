package com.consentradar.consentradar.crawler;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyChangeDetectionServiceTest {

    @Mock
    private PolicySnapshotRepository policySnapshotRepository;

    private final Company company = new Company();

    PolicyChangeDetectionServiceTest() {
        company.setCompanyId(1L);
        company.setCompanyName("테스트기업");
    }

    @Test
    void detectAndSave_insertsNewSnapshotWhenTextChanged() {
        PolicySnapshot previous = new PolicySnapshot();
        previous.setCompany(company);
        previous.setRawText("이전 약관 내용입니다.");
        previous.setCrawledAt(LocalDateTime.now().minusDays(1));

        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(1L))
                .thenReturn(Optional.of(previous));
        when(policySnapshotRepository.save(any(PolicySnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PolicySnapshot result = new PolicyChangeDetectionService(policySnapshotRepository)
                .detectAndSave(company, "변경된 새로운 약관 내용입니다.");

        ArgumentCaptor<PolicySnapshot> captor = ArgumentCaptor.forClass(PolicySnapshot.class);
        verify(policySnapshotRepository, times(1)).save(captor.capture());

        assertTrue(result.isChanged());
        assertEquals("변경된 새로운 약관 내용입니다.", captor.getValue().getRawText());
        assertTrue(captor.getValue().isChanged());
    }

    @Test
    void detectAndSave_updatesCrawledAtOnlyWhenTextUnchanged() {
        String sameText = "동일한 약관 내용입니다.";
        LocalDateTime oldCrawledAt = LocalDateTime.now().minusDays(1);

        PolicySnapshot previous = new PolicySnapshot();
        previous.setCompany(company);
        previous.setRawText(sameText);
        previous.setCrawledAt(oldCrawledAt);

        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(1L))
                .thenReturn(Optional.of(previous));
        when(policySnapshotRepository.save(any(PolicySnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PolicySnapshot result = new PolicyChangeDetectionService(policySnapshotRepository)
                .detectAndSave(company, sameText);

        verify(policySnapshotRepository, times(1)).save(previous);
        verify(policySnapshotRepository, never()).save(argThat(s -> s != previous));

        assertFalse(result.isChanged());
        assertTrue(result.getCrawledAt().isAfter(oldCrawledAt));
    }

    @Test
    void detectAndSave_insertsBaselineSnapshotWhenNoPreviousExists() {
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(1L))
                .thenReturn(Optional.empty());
        when(policySnapshotRepository.save(any(PolicySnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PolicySnapshot result = new PolicyChangeDetectionService(policySnapshotRepository)
                .detectAndSave(company, "최초 수집된 약관 내용입니다.");

        verify(policySnapshotRepository, times(1)).save(any(PolicySnapshot.class));
        assertFalse(result.isChanged());
    }
}