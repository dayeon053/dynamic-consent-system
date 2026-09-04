package com.consentradar.consentradar.scheduler;

import com.consentradar.consentradar.crawler.PolicyCrawlException;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PolicyCrawlScheduler}는 기업 1건 처리를 {@link PolicyCrawlProcessor}에 위임하는 얇은
 * 오케스트레이터라, 여기서는 "전체 기업을 도는 루프/성공·실패 집계/위임 여부"만 검증한다.
 * "언제 위험도 재산출을 트리거하는가"의 비즈니스 로직은 {@link PolicyCrawlProcessorTest}가 담당한다.
 */
@ExtendWith(MockitoExtension.class)
class PolicyCrawlSchedulerTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private PolicyCrawlProcessor policyCrawlProcessor;

    private PolicyCrawlScheduler newScheduler() {
        return new PolicyCrawlScheduler(companyRepository, policyCrawlProcessor);
    }

    @Test
    void runPipeline_processesAllCompaniesAndReportsCounts() {
        List<Company> companies = createCompanies(5);
        when(companyRepository.findAll()).thenReturn(companies);
        when(policyCrawlProcessor.processCompany(any(Company.class)))
                .thenAnswer(inv -> {
                    Company c = inv.getArgument(0);
                    return new CompanyCrawlResult(c.getCompanyId(), c.getCompanyName(), false, false);
                });

        PipelineRunResult result = newScheduler().runPipeline();

        assertEquals(5, result.totalCompanies());
        assertEquals(5, result.successCount());
        assertEquals(0, result.failCount());
        assertFalse(result.finishedAt().isBefore(result.startedAt()));
        verify(policyCrawlProcessor, times(5)).processCompany(any(Company.class));
    }

    @Test
    void runPipeline_continuesAndCountsFailuresWhenCrawlThrows() {
        List<Company> companies = createCompanies(5);
        when(companyRepository.findAll()).thenReturn(companies);
        when(policyCrawlProcessor.processCompany(any(Company.class)))
                .thenAnswer(inv -> {
                    Company c = inv.getArgument(0);
                    return new CompanyCrawlResult(c.getCompanyId(), c.getCompanyName(), false, false);
                })
                .thenThrow(new PolicyCrawlException("크롤링 실패", new RuntimeException()))
                .thenAnswer(inv -> {
                    Company c = inv.getArgument(0);
                    return new CompanyCrawlResult(c.getCompanyId(), c.getCompanyName(), false, false);
                })
                .thenAnswer(inv -> {
                    Company c = inv.getArgument(0);
                    return new CompanyCrawlResult(c.getCompanyId(), c.getCompanyName(), false, false);
                })
                .thenAnswer(inv -> {
                    Company c = inv.getArgument(0);
                    return new CompanyCrawlResult(c.getCompanyId(), c.getCompanyName(), false, false);
                });

        PipelineRunResult result = newScheduler().runPipeline();

        assertEquals(5, result.totalCompanies());
        assertEquals(4, result.successCount());
        assertEquals(1, result.failCount());
        verify(policyCrawlProcessor, times(5)).processCompany(any(Company.class));
    }

    @Test
    void runForCompany_delegatesToProcessor_andReturnsItsResult() {
        Company company = createCompanies(1).get(0);
        when(companyRepository.findById(company.getCompanyId())).thenReturn(Optional.of(company));
        CompanyCrawlResult expected =
                new CompanyCrawlResult(company.getCompanyId(), company.getCompanyName(), true, true);
        when(policyCrawlProcessor.processCompany(company, false)).thenReturn(expected);

        CompanyCrawlResult result = newScheduler().runForCompany(company.getCompanyId());

        assertEquals(expected.companyId(), result.companyId());
        assertTrue(result.changed());
        assertTrue(result.riskAnalysisTriggered());
    }

    @Test
    void runForCompany_throwsIllegalArgumentException_whenCompanyNotFound() {
        when(companyRepository.findById(999L)).thenReturn(Optional.empty());

        PolicyCrawlScheduler scheduler = newScheduler();
        assertThrows(IllegalArgumentException.class, () -> scheduler.runForCompany(999L));
        verify(policyCrawlProcessor, never()).processCompany(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    /**
     * force=true 관리자 강제 재분석 옵션 — POST /admin/crawl/{id}?force=true가 여기까지
     * 그대로 전달되는지 검증한다.
     */
    @Test
    void runForCompanyWithForce_passesForceFlagThrough_toProcessor() {
        Company company = createCompanies(1).get(0);
        when(companyRepository.findById(company.getCompanyId())).thenReturn(Optional.of(company));
        CompanyCrawlResult expected =
                new CompanyCrawlResult(company.getCompanyId(), company.getCompanyName(), false, true);
        when(policyCrawlProcessor.processCompany(company, true)).thenReturn(expected);

        CompanyCrawlResult result = newScheduler().runForCompany(company.getCompanyId(), true);

        assertFalse(result.changed());
        assertTrue(result.riskAnalysisTriggered(), "force=true면 changed=false여도 재분석이 실행됐다는 결과가 와야 한다");
        verify(policyCrawlProcessor).processCompany(company, true);
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
