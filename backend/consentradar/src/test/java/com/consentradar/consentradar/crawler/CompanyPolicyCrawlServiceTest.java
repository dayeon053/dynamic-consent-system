package com.consentradar.consentradar.crawler;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyPolicyCrawlServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private PolicySnapshotRepository policySnapshotRepository;

    @Mock
    private PolicyBodyCrawler policyBodyCrawler;

    @Test
    void crawlAll_succeedsForAtLeastFiveCompanies() {
        List<Company> companies = createCompanies(5);
        when(companyRepository.findAll()).thenReturn(companies);
        when(policyBodyCrawler.fetchCleanText(anyString()))
                .thenReturn("개인정보처리방침 본문 텍스트입니다.");

        CompanyPolicyCrawlService service =
                new CompanyPolicyCrawlService(companyRepository, policySnapshotRepository, policyBodyCrawler);

        CrawlBatchResult result = service.crawlAll();

        assertEquals(5, result.totalCompanies());
        assertEquals(5, result.successCount());
        assertEquals(0, result.failCount());
        verify(policySnapshotRepository, times(5)).save(any(PolicySnapshot.class));
    }

    @Test
    void crawlAll_countsFailuresWithoutStoppingBatch() {
        List<Company> companies = createCompanies(5);
        when(companyRepository.findAll()).thenReturn(companies);
        when(policyBodyCrawler.fetchCleanText(anyString()))
                .thenReturn("본문")
                .thenReturn("본문")
                .thenThrow(new PolicyCrawlException("실패", new RuntimeException()))
                .thenReturn("본문")
                .thenReturn("본문");

        CompanyPolicyCrawlService service =
                new CompanyPolicyCrawlService(companyRepository, policySnapshotRepository, policyBodyCrawler);

        CrawlBatchResult result = service.crawlAll();

        assertEquals(5, result.totalCompanies());
        assertEquals(4, result.successCount());
        assertEquals(1, result.failCount());
        verify(policySnapshotRepository, times(4)).save(any(PolicySnapshot.class));
    }

    private List<Company> createCompanies(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> {
                    Company company = new Company();
                    company.setCompanyName("기업" + i);
                    company.setPackageName("com.example.company" + i);
                    company.setPrivacyUrl("https://example" + i + ".com/privacy");
                    return company;
                })
                .toList();
    }
}
