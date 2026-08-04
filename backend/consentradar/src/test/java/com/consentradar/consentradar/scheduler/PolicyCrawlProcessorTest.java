package com.consentradar.consentradar.scheduler;

import com.consentradar.consentradar.crawler.PolicyBodyCrawler;
import com.consentradar.consentradar.crawler.PolicyChangeDetectionService;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.pipeline.RiskPipelineService;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PolicyCrawlProcessor}의 "언제 위험도 재산출을 트리거하는가" 비즈니스 로직 단위 테스트.
 * 트랜잭션 경계 자체(실패 시 롤백 여부)는 실제 DB가 필요해 이 단위 테스트로는 검증할 수 없고
 * {@code PolicyCrawlSchedulerTransactionBoundaryIntegrationTest}가 담당한다.
 */
@ExtendWith(MockitoExtension.class)
class PolicyCrawlProcessorTest {

    @Mock
    private PolicyBodyCrawler policyBodyCrawler;

    @Mock
    private PolicyChangeDetectionService policyChangeDetectionService;

    @Mock
    private PolicySnapshotRepository policySnapshotRepository;

    @Mock
    private RiskPipelineService riskPipelineService;

    private PolicyCrawlProcessor newProcessor() {
        return new PolicyCrawlProcessor(
                policyBodyCrawler, policyChangeDetectionService, policySnapshotRepository, riskPipelineService);
    }

    private Company company() {
        Company company = new Company();
        company.setCompanyId(1L);
        company.setCompanyName("기업1");
        company.setPrivacyUrl("https://example1.com/privacy");
        return company;
    }

    @Test
    void processCompany_skipsRiskAnalysis_whenAlreadyKnownAndUnchanged() {
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("본문 텍스트");
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.of(new PolicySnapshot()));
        PolicySnapshot unchanged = new PolicySnapshot();
        unchanged.setChanged(false);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString())).thenReturn(unchanged);

        CompanyCrawlResult result = newProcessor().processCompany(company());

        assertFalse(result.changed());
        assertFalse(result.riskAnalysisTriggered());
        verify(riskPipelineService, never()).analyzeAndSaveRisk(any(Company.class), anyString());
    }

    @Test
    void processCompany_triggersRiskAnalysis_whenPolicyChanged() {
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("변경된 본문");
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.of(new PolicySnapshot()));
        PolicySnapshot changed = new PolicySnapshot();
        changed.setChanged(true);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString())).thenReturn(changed);

        CompanyCrawlResult result = newProcessor().processCompany(company());

        assertTrue(result.changed());
        assertTrue(result.riskAnalysisTriggered());
        verify(riskPipelineService, times(1)).analyzeAndSaveRisk(any(Company.class), eq("변경된 본문"));
    }

    @Test
    void processCompany_triggersRiskAnalysis_forFirstTimeCollectionEvenWhenNotChanged() {
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("최초 수집 본문");
        // 최초 수집: 이전 스냅샷 없음
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.empty());
        // detectAndSave는 최초 수집 시 isChanged=false로 저장하는 계약(PolicyChangeDetectionService)
        PolicySnapshot baseline = new PolicySnapshot();
        baseline.setChanged(false);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString())).thenReturn(baseline);

        CompanyCrawlResult result = newProcessor().processCompany(company());

        assertFalse(result.changed());
        assertTrue(result.riskAnalysisTriggered(), "최초 수집이면 변경 여부와 무관하게 재산출해야 한다");
        verify(riskPipelineService, times(1)).analyzeAndSaveRisk(any(Company.class), eq("최초 수집 본문"));
    }

    @Test
    void processCompany_returnsCompanyIdAndName_matchingInput() {
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("본문");
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.of(new PolicySnapshot()));
        PolicySnapshot changed = new PolicySnapshot();
        changed.setChanged(true);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString())).thenReturn(changed);
        Company company = company();

        CompanyCrawlResult result = newProcessor().processCompany(company);

        assertEquals(company.getCompanyId(), result.companyId());
        assertEquals(company.getCompanyName(), result.companyName());
        verify(riskPipelineService, times(1)).analyzeAndSaveRisk(company, "본문");
    }
}
