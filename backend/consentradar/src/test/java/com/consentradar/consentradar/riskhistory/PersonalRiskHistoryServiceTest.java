package com.consentradar.consentradar.riskhistory;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.RiskScore;
import com.consentradar.consentradar.entity.User;
import com.consentradar.consentradar.repository.RiskScoreRepository;
import com.dynamicconsent.model.RiskGrade;
import com.dynamicconsent.model.RiskResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalRiskHistoryServiceTest {

    @Mock
    private RiskScoreRepository riskScoreRepository;

    @Test
    void saveIfAbsent_savesNewRepresentativeRow_whenNoneExistsForToday() {
        User user = new User();
        user.setUserId(1L);
        Company company = new Company();
        company.setCompanyId(2L);
        RiskResult result = new RiskResult(20.0, RiskGrade.MEDIUM);

        when(riskScoreRepository.existsByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                eq(1L), eq(2L), eq(LocalDate.now()))).thenReturn(false);
        when(riskScoreRepository.save(any(RiskScore.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PersonalRiskHistoryService service = new PersonalRiskHistoryService(riskScoreRepository);
        Optional<RiskScore> saved = service.saveIfAbsent(user, company, result);

        assertTrue(saved.isPresent());
        assertEquals(user, saved.get().getUser());
        assertEquals(company, saved.get().getCompany());
        assertEquals(LocalDate.now(), saved.get().getScoredAt());
        assertTrue(saved.get().isRepresentative());
        assertEquals(RiskScore.Grade.MEDIUM, saved.get().getGrade());
        verify(riskScoreRepository, times(1)).save(any(RiskScore.class));
    }

    @Test
    void saveIfAbsent_skipsSave_whenTodaysRepresentativeRowAlreadyExists() {
        User user = new User();
        user.setUserId(1L);
        Company company = new Company();
        company.setCompanyId(2L);
        RiskResult result = new RiskResult(20.0, RiskGrade.MEDIUM);

        when(riskScoreRepository.existsByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                eq(1L), eq(2L), eq(LocalDate.now()))).thenReturn(true);

        PersonalRiskHistoryService service = new PersonalRiskHistoryService(riskScoreRepository);
        Optional<RiskScore> saved = service.saveIfAbsent(user, company, result);

        assertTrue(saved.isEmpty());
        verify(riskScoreRepository, never()).save(any(RiskScore.class));
    }

    @Test
    void saveOrUpdateToday_createsNewRow_whenNoneExistsForToday() {
        User user = new User();
        user.setUserId(1L);
        Company company = new Company();
        company.setCompanyId(2L);
        RiskResult result = new RiskResult(20.0, RiskGrade.MEDIUM);

        when(riskScoreRepository.findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                eq(1L), eq(2L), eq(LocalDate.now()))).thenReturn(Optional.empty());
        when(riskScoreRepository.saveAndFlush(any(RiskScore.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PersonalRiskHistoryService service = new PersonalRiskHistoryService(riskScoreRepository);
        RiskScore saved = service.saveOrUpdateToday(user, company, result);

        assertEquals(user, saved.getUser());
        assertEquals(company, saved.getCompany());
        assertEquals(LocalDate.now(), saved.getScoredAt());
        assertTrue(saved.isRepresentative());
        assertEquals(RiskScore.Grade.MEDIUM, saved.getGrade());
        verify(riskScoreRepository, times(1)).saveAndFlush(any(RiskScore.class));
    }

    @Test
    void saveOrUpdateToday_updatesExistingTodayRow_ratherThanCreatingNew() {
        User user = new User();
        user.setUserId(1L);
        Company company = new Company();
        company.setCompanyId(2L);
        RiskScore existingToday = new RiskScore();
        existingToday.setUser(user);
        existingToday.setCompany(company);
        existingToday.setScoredAt(LocalDate.now());
        existingToday.setRepresentative(true);
        existingToday.setTotalScore(java.math.BigDecimal.valueOf(10.0));
        existingToday.setGrade(RiskScore.Grade.LOW);

        when(riskScoreRepository.findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                eq(1L), eq(2L), eq(LocalDate.now()))).thenReturn(Optional.of(existingToday));
        when(riskScoreRepository.saveAndFlush(any(RiskScore.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PersonalRiskHistoryService service = new PersonalRiskHistoryService(riskScoreRepository);
        RiskScore saved = service.saveOrUpdateToday(user, company, new RiskResult(35.0, RiskGrade.HIGH));

        assertEquals(existingToday, saved, "새로 만들지 않고 오늘자 기존 row를 그대로 갱신해야 한다");
        assertEquals(0, java.math.BigDecimal.valueOf(35.0).compareTo(saved.getTotalScore()));
        assertEquals(RiskScore.Grade.HIGH, saved.getGrade());
        verify(riskScoreRepository, times(1)).saveAndFlush(existingToday);
    }

    @Test
    void getHistory_mapsRepositoryResultsToDtosInOrder() {
        RiskScore day1 = new RiskScore();
        day1.setScoredAt(LocalDate.of(2026, 7, 1));
        day1.setTotalScore(java.math.BigDecimal.valueOf(10.0));
        day1.setGrade(RiskScore.Grade.LOW);

        RiskScore day2 = new RiskScore();
        day2.setScoredAt(LocalDate.of(2026, 7, 2));
        day2.setTotalScore(java.math.BigDecimal.valueOf(15.0));
        day2.setGrade(RiskScore.Grade.MEDIUM);

        when(riskScoreRepository
                .findByUser_UserIdAndCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtAsc(1L, 2L))
                .thenReturn(List.of(day1, day2));

        PersonalRiskHistoryService service = new PersonalRiskHistoryService(riskScoreRepository);
        List<RiskScoreHistoryItemDto> history = service.getHistory(1L, 2L);

        assertEquals(2, history.size());
        assertEquals(LocalDate.of(2026, 7, 1), history.get(0).scoredAt());
        assertEquals(LocalDate.of(2026, 7, 2), history.get(1).scoredAt());
    }
}
