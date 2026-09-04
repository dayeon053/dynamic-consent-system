package com.consentradar.consentradar.scheduler;

import com.consentradar.consentradar.api.PersonalRiskCalculator;
import com.consentradar.consentradar.crawler.PolicyBodyCrawler;
import com.consentradar.consentradar.crawler.PolicyChangeDetectionService;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.entity.User;
import com.consentradar.consentradar.pipeline.RiskPipelineService;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import com.consentradar.consentradar.repository.UserConsentCheckRepository;
import com.consentradar.consentradar.riskhistory.PersonalRiskHistoryService;
import com.dynamicconsent.model.RiskGrade;
import com.dynamicconsent.model.RiskResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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

    @Mock
    private UserConsentCheckRepository userConsentCheckRepository;

    @Mock
    private PersonalRiskCalculator personalRiskCalculator;

    @Mock
    private PersonalRiskHistoryService personalRiskHistoryService;

    private PolicyCrawlProcessor newProcessor() {
        return new PolicyCrawlProcessor(
                policyBodyCrawler, policyChangeDetectionService, policySnapshotRepository, riskPipelineService,
                userConsentCheckRepository, personalRiskCalculator, personalRiskHistoryService);
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

    /**
     * [force=true 관리자 강제 재분석] 재크롤링 텍스트가 동일해도(changed=false) 예전 오염된
     * ConsentItem을 정리해야 하는 관리 목적으로, force=true면 shouldAnalyze 판단을 건너뛰고
     * 무조건 analyzeAndSaveRisk()를 실행해야 한다.
     */
    @Test
    void processCompanyWithForce_triggersRiskAnalysis_evenWhenUnchanged() {
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("변경 없는 본문");
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.of(new PolicySnapshot()));
        PolicySnapshot unchanged = new PolicySnapshot();
        unchanged.setChanged(false);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString())).thenReturn(unchanged);

        CompanyCrawlResult result = newProcessor().processCompany(company(), true);

        assertFalse(result.changed(), "changed 필드는 실제 텍스트 변경 여부를 그대로 반영해야 한다(false)");
        assertTrue(result.riskAnalysisTriggered(), "force=true면 changed=false여도 재분석이 실행돼야 한다");
        verify(riskPipelineService, times(1)).analyzeAndSaveRisk(any(Company.class), eq("변경 없는 본문"));
    }

    /** force=false(기본값)면 기존 로직 그대로 — changed=false일 때 스킵돼야 한다. */
    @Test
    void processCompanyWithoutForce_skipsRiskAnalysis_whenUnchanged() {
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("변경 없는 본문");
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.of(new PolicySnapshot()));
        PolicySnapshot unchanged = new PolicySnapshot();
        unchanged.setChanged(false);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString())).thenReturn(unchanged);

        CompanyCrawlResult result = newProcessor().processCompany(company(), false);

        assertFalse(result.riskAnalysisTriggered());
        verify(riskPipelineService, never()).analyzeAndSaveRisk(any(Company.class), anyString());
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

    // ---- 개인 맞춤 위험도 히스토리 배치 연결 ----

    @Test
    void processCompany_savesPersonalRiskHistory_forEachInterestedUser_evenWhenPolicyUnchanged() {
        // Q3 결정: 정책 변경 여부(shouldAnalyze)와 무관하게 매일 밤 무조건 실행되어야 한다.
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("본문 텍스트");
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.of(new PolicySnapshot()));
        PolicySnapshot unchanged = new PolicySnapshot();
        unchanged.setChanged(false);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString())).thenReturn(unchanged);

        Company company = company();
        User userA = user(1L);
        User userB = user(2L);
        when(userConsentCheckRepository.findDistinctUsersByConsentItem_Company_CompanyId(company.getCompanyId()))
                .thenReturn(List.of(userA, userB));
        RiskResult resultA = new RiskResult(10.0, RiskGrade.LOW);
        RiskResult resultB = new RiskResult(20.0, RiskGrade.MEDIUM);
        when(personalRiskCalculator.calculate(1L, company.getCompanyId())).thenReturn(resultA);
        when(personalRiskCalculator.calculate(2L, company.getCompanyId())).thenReturn(resultB);

        newProcessor().processCompany(company);

        verify(riskPipelineService, never()).analyzeAndSaveRisk(any(Company.class), anyString());
        verify(personalRiskHistoryService).saveIfAbsent(userA, company, resultA);
        verify(personalRiskHistoryService).saveIfAbsent(userB, company, resultB);
    }

    @Test
    void processCompany_skipsPersonalRiskHistorySave_whenCalculatorReturnsNull() {
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("본문 텍스트");
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.of(new PolicySnapshot()));
        PolicySnapshot unchanged = new PolicySnapshot();
        unchanged.setChanged(false);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString())).thenReturn(unchanged);

        Company company = company();
        User userA = user(1L);
        when(userConsentCheckRepository.findDistinctUsersByConsentItem_Company_CompanyId(company.getCompanyId()))
                .thenReturn(List.of(userA));
        when(personalRiskCalculator.calculate(1L, company.getCompanyId())).thenReturn(null);

        newProcessor().processCompany(company);

        verify(personalRiskHistoryService, never()).saveIfAbsent(any(User.class), any(Company.class), any());
    }

    @Test
    void processCompany_skipsOnlyTheAffectedUser_whenPersonalRiskCalculationFailsForOneUser() {
        // 사용자 한 명의 동의 항목 데이터가 잘못돼 있어도(IllegalArgumentException) 이 기업의
        // 크롤링/위험도 재산출 자체는 실패하지 않고, 나머지 정상 사용자는 그대로 저장돼야 한다.
        when(policyBodyCrawler.fetchCleanText(anyString())).thenReturn("본문 텍스트");
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(anyLong()))
                .thenReturn(Optional.of(new PolicySnapshot()));
        PolicySnapshot unchanged = new PolicySnapshot();
        unchanged.setChanged(false);
        when(policyChangeDetectionService.detectAndSave(any(Company.class), anyString())).thenReturn(unchanged);

        Company company = company();
        User corruptedUser = user(1L);
        User normalUser = user(2L);
        when(userConsentCheckRepository.findDistinctUsersByConsentItem_Company_CompanyId(company.getCompanyId()))
                .thenReturn(List.of(corruptedUser, normalUser));
        when(personalRiskCalculator.calculate(1L, company.getCompanyId()))
                .thenThrow(new IllegalArgumentException("유효하지 않은 DS 점수"));
        RiskResult normalResult = new RiskResult(10.0, RiskGrade.LOW);
        when(personalRiskCalculator.calculate(2L, company.getCompanyId())).thenReturn(normalResult);

        CompanyCrawlResult result = newProcessor().processCompany(company);

        assertEquals(company.getCompanyId(), result.companyId(), "한 사용자의 계산 실패가 전체 결과에 영향을 주면 안 된다");
        verify(personalRiskHistoryService, never()).saveIfAbsent(eq(corruptedUser), any(Company.class), any());
        verify(personalRiskHistoryService).saveIfAbsent(normalUser, company, normalResult);
    }

    private User user(Long id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }
}
