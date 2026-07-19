package com.consentradar.consentradar.scheduler;

import com.consentradar.consentradar.crawler.PolicyBodyCrawler;
import com.consentradar.consentradar.crawler.PolicyChangeDetectionService;
import com.consentradar.consentradar.crawler.PolicyCrawlException;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyCrawlSchedulerTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private PolicyBodyCrawler policyBodyCrawler;

    @Mock
    private PolicyChangeDetectionService policyChangeDetectionService;

    @Test
    void runPipeline_processesAllCompaniesAndReportsCounts() {
        List<Company> companies = createCompanies(5);
        when(companyRepository.findAll()).thenReturn(companies);
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("본문 텍스트");

        PolicyCrawlScheduler scheduler =
                new PolicyCrawlScheduler(companyRepository, policyBodyCrawler, policyChangeDetectionService);

        PipelineRunResult result = scheduler.runPipeline();

        assertEquals(5, result.totalCompanies());
        assertEquals(5, result.successCount());
        assertEquals(0, result.failCount());
        assertFalse(result.finishedAt().isBefore(result.startedAt()));
        verify(policyChangeDetectionService, times(5)).detectAndSave(any(Company.class), anyString());
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

        PolicyCrawlScheduler scheduler =
                new PolicyCrawlScheduler(companyRepository, policyBodyCrawler, policyChangeDetectionService);

        PipelineRunResult result = scheduler.runPipeline();

        assertEquals(5, result.totalCompanies());
        assertEquals(4, result.successCount());
        assertEquals(1, result.failCount());
        verify(policyChangeDetectionService, times(4)).detectAndSave(any(Company.class), anyString());
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