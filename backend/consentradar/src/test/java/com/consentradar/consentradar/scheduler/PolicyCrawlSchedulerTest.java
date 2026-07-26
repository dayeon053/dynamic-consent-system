package com.consentradar.consentradar.scheduler;

import com.consentradar.consentradar.crawler.PolicyBodyCrawler;
import com.consentradar.consentradar.crawler.PolicyChangeDetectionService;
import com.consentradar.consentradar.crawler.PolicyCrawlException;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.pipeline.RiskPipelineService;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyCrawlSchedulerTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private PolicyBodyCrawler policyBodyCrawler;

    @Mock
    private PolicyChangeDetectionService policyChangeDetectionService;

    @Mock
    private PolicySnapshotRepository policySnapshotRepository;

    @Mock
    private RiskPipelineService riskPipelineService;

    private PolicyCrawlScheduler newScheduler() {
        return new PolicyCrawlScheduler(
                companyRepository, policyBodyCrawler, policyChangeDetectionService,
                policySnapshotRepository, riskPipelineService);
    }

    /** 최초 수집이 아니고 변경도 없는 "이미 알려진" 스냅샷 — 위험도 재산출이 트리거되지 않아야 하는 기본 상태. */
    private void stubAsAlreadyKnownAndUnchanged() {
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.of(new PolicySnapshot()));
        PolicySnapshot unchanged = new PolicySnapshot();
        unchanged.setChanged(false);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString()))
                .thenReturn(unchanged);
    }

    @Test
    void runPipeline_processesAllCompaniesAndReportsCounts() {
        List<Company> companies = createCompanies(5);
        when(companyRepository.findAll()).thenReturn(companies);
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("본문 텍스트");
        stubAsAlreadyKnownAndUnchanged();

        PipelineRunResult result = newScheduler().runPipeline();

        assertEquals(5, result.totalCompanies());
        assertEquals(5, result.successCount());
        assertEquals(0, result.failCount());
        assertFalse(result.finishedAt().isBefore(result.startedAt()));
        verify(policyChangeDetectionService, times(5)).detectAndSave(any(Company.class), anyString());
        verify(riskPipelineService, never()).analyzeAndSaveRisk(any(Company.class), anyString());
    }

    @Test
    void runPipeline_continuesAndCountsFailuresWhenCrawlThrows() {
        List<Company> companies = createCompanies(5);
        when(companyRepository.findAll()).thenReturn(companies);
        when(policyBodyCrawler.fetchCleanText(anyString()))
                .thenReturn("본문")
                .thenThrow(new PolicyCrawlException("크롤링 실패", new RuntimeException()))
                .thenReturn("본문")
                .thenReturn("본문")
                .thenReturn("본문");
        stubAsAlreadyKnownAndUnchanged();

        PipelineRunResult result = newScheduler().runPipeline();

        assertEquals(5, result.totalCompanies());
        assertEquals(4, result.successCount());
        assertEquals(1, result.failCount());
        verify(policyChangeDetectionService, times(4)).detectAndSave(any(Company.class), anyString());
    }

    @Test
    void runPipeline_triggersRiskAnalysis_whenPolicyChanged() {
        List<Company> companies = createCompanies(1);
        when(companyRepository.findAll()).thenReturn(companies);
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("변경된 본문");
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.of(new PolicySnapshot()));
        PolicySnapshot changed = new PolicySnapshot();
        changed.setChanged(true);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString())).thenReturn(changed);

        newScheduler().runPipeline();

        verify(riskPipelineService, times(1)).analyzeAndSaveRisk(any(Company.class), eq("변경된 본문"));
    }

    @Test
    void runPipeline_triggersRiskAnalysis_forFirstTimeCollectionEvenWhenNotChanged() {
        List<Company> companies = createCompanies(1);
        when(companyRepository.findAll()).thenReturn(companies);
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("최초 수집 본문");
        // 최초 수집: 이전 스냅샷 없음
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.empty());
        // detectAndSave는 최초 수집 시 isChanged=false로 저장하는 계약(PolicyChangeDetectionService)
        PolicySnapshot baseline = new PolicySnapshot();
        baseline.setChanged(false);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString())).thenReturn(baseline);

        newScheduler().runPipeline();

        verify(riskPipelineService, times(1)).analyzeAndSaveRisk(any(Company.class), eq("최초 수집 본문"));
    }

    @Test
    void runForCompany_triggersAnalysisAndReturnsResult_whenChanged() {
        Company company = createCompanies(1).get(0);
        when(companyRepository.findById(company.getCompanyId())).thenReturn(Optional.of(company));
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("본문");
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.of(new PolicySnapshot()));
        PolicySnapshot changed = new PolicySnapshot();
        changed.setChanged(true);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString())).thenReturn(changed);

        CompanyCrawlResult result = newScheduler().runForCompany(company.getCompanyId());

        assertEquals(company.getCompanyId(), result.companyId());
        assertTrue(result.changed());
        assertTrue(result.riskAnalysisTriggered());
        verify(riskPipelineService, times(1)).analyzeAndSaveRisk(company, "본문");
    }

    @Test
    void runForCompany_throwsIllegalArgumentException_whenCompanyNotFound() {
        when(companyRepository.findById(999L)).thenReturn(Optional.empty());

        PolicyCrawlScheduler scheduler = newScheduler();
        assertThrows(IllegalArgumentException.class, () -> scheduler.runForCompany(999L));
        verify(riskPipelineService, never()).analyzeAndSaveRisk(any(), anyString());
    }

    private List<Company> createCompanies(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> {
                    Company company = new Company();
                    company.setCompanyId((long) i);
                    company.setCompanyName("기업" + i);
                    company.setPrivacyUrl("https://example" + i + ".com/privacy");
                    return company;
                })
                .toList();
    }
}
