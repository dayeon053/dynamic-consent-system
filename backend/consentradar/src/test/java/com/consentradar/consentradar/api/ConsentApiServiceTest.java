package com.consentradar.consentradar.api;

import com.consentradar.consentradar.api.dto.CompanyRiskResponse;
import com.consentradar.consentradar.api.dto.ConsentItemResponse;
import com.consentradar.consentradar.api.dto.ConsentPatchResponse;
import com.consentradar.consentradar.consenthistory.UserConsentHistoryRecorder;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.entity.RiskScore;
import com.consentradar.consentradar.entity.User;
import com.consentradar.consentradar.entity.UserConsentCheck;
import com.consentradar.consentradar.repository.*;
import com.consentradar.consentradar.riskhistory.PersonalRiskHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentApiServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ConsentItemRepository consentItemRepository;
    @Mock private UserConsentCheckRepository userConsentCheckRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private PolicySnapshotRepository policySnapshotRepository;
    @Mock private RiskScoreRepository riskScoreRepository;
    @Mock private UserConsentHistoryRecorder userConsentHistoryRecorder;

    private ConsentApiService consentApiService;

    private static final Long USER_ID = 1L;
    private static final Long COMPANY_ID = 10L;

    @BeforeEach
    void setUp() {
        // PersonalRiskCalculator/PersonalRiskHistoryService는 실제 인스턴스를 써서 mock
        // 리포지토리 스텁이 그대로 통하게 한다(getConsentItems/toggleConsent가 내부적으로
        // 이 컴포넌트들에 위임하기 때문).
        PersonalRiskCalculator personalRiskCalculator =
                new PersonalRiskCalculator(consentItemRepository, userConsentCheckRepository);
        PersonalRiskHistoryService personalRiskHistoryService =
                new PersonalRiskHistoryService(riskScoreRepository);
        consentApiService = new ConsentApiService(
                userRepository, consentItemRepository, userConsentCheckRepository,
                companyRepository, policySnapshotRepository, personalRiskCalculator,
                userConsentHistoryRecorder, personalRiskHistoryService);
    }

    @Test
    void getConsentItems_returnsRequiredItemAlwaysChecked_evenWithNoUserCheckRecord() {
        ConsentItem required = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(required));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());

        List<ConsentItemResponse> result = consentApiService.getConsentItems(USER_ID, COMPANY_ID);

        assertEquals(1, result.size());
        assertTrue(result.get(0).isChecked());
    }

    @Test
    void getConsentItems_returnsOptionalItemChecked_onlyWhenUserActuallyCheckedIt() {
        ConsentItem checkedOptional = consentItem(1L, ConsentItem.ItemType.OPTIONAL);
        ConsentItem uncheckedOptional = consentItem(2L, ConsentItem.ItemType.OPTIONAL);
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID))
                .thenReturn(List.of(checkedOptional, uncheckedOptional));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(userConsentCheck(checkedOptional, true), userConsentCheck(uncheckedOptional, false)));

        List<ConsentItemResponse> result = consentApiService.getConsentItems(USER_ID, COMPANY_ID);

        assertEquals(2, result.size());
        assertTrue(result.stream().filter(r -> r.getConsentItemId().equals(1L)).findFirst().orElseThrow().isChecked());
        assertTrue(result.stream().filter(r -> r.getConsentItemId().equals(2L)).findFirst().orElseThrow().isChecked() == false);
    }

    @Test
    void getConsentItems_mapsAllFiveVariableScores() {
        ConsentItem item = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        item.setDsScore(5);
        item.setEsScore(3);
        item.setTfScore(2);
        item.setPcScore(1.5);
        item.setAiScore(1.0);
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(item));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of());

        ConsentItemResponse response = consentApiService.getConsentItems(USER_ID, COMPANY_ID).get(0);

        assertEquals(5, response.getDsScore());
        assertEquals(3, response.getEsScore());
        assertEquals(2, response.getTfScore());
        assertEquals(1.5, response.getPcScore());
        assertEquals(1.0, response.getAiScore());
    }

    // ---- toggleConsent ----

    @Test
    void toggleConsent_flipsExistingCheck_fromUncheckedToChecked() {
        ConsentItem required = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        UserConsentCheck existing = userConsentCheck(required, false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(1L)).thenReturn(Optional.of(required));
        when(userConsentCheckRepository.findByUser_UserIdAndConsentItem_ConsentItemId(USER_ID, 1L))
                .thenReturn(Optional.of(existing));
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(required));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(existing));
        when(riskScoreRepository.findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                USER_ID, COMPANY_ID, LocalDate.now())).thenReturn(Optional.empty());

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 1L, null);

        assertTrue(response.isChecked());
        assertTrue(existing.isChecked(), "저장된 체크 레코드도 true로 반전돼야 한다");
    }

    @Test
    void toggleConsent_flipsExistingCheck_fromCheckedToUnchecked() {
        ConsentItem required = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        UserConsentCheck existing = userConsentCheck(required, true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(1L)).thenReturn(Optional.of(required));
        when(userConsentCheckRepository.findByUser_UserIdAndConsentItem_ConsentItemId(USER_ID, 1L))
                .thenReturn(Optional.of(existing));
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(required));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(existing));
        when(riskScoreRepository.findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                USER_ID, COMPANY_ID, LocalDate.now())).thenReturn(Optional.empty());

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 1L, null);

        assertFalse(response.isChecked());
        assertFalse(existing.isChecked());
    }

    @Test
    void toggleConsent_setsExplicitCheckedTrue_ratherThanFlipping_evenWhenAlreadyTrue() {
        // PATCH 멱등성 개선(A안): desiredChecked가 주어지면 현재 상태와 무관하게 그 값 그대로
        // 저장돼야 한다 — 이미 true인 상태에서 checked=true를 또 보내도(재시도 등) false로
        // 뒤집히면 안 된다.
        ConsentItem required = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        UserConsentCheck existing = userConsentCheck(required, true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(1L)).thenReturn(Optional.of(required));
        when(userConsentCheckRepository.findByUser_UserIdAndConsentItem_ConsentItemId(USER_ID, 1L))
                .thenReturn(Optional.of(existing));
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(required));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(existing));
        when(riskScoreRepository.findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                USER_ID, COMPANY_ID, LocalDate.now())).thenReturn(Optional.empty());

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 1L, true);

        assertTrue(response.isChecked());
        assertTrue(existing.isChecked());
    }

    @Test
    void toggleConsent_setsExplicitCheckedFalse_ratherThanFlipping_evenWhenAlreadyFalse() {
        ConsentItem required = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        UserConsentCheck existing = userConsentCheck(required, false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(1L)).thenReturn(Optional.of(required));
        when(userConsentCheckRepository.findByUser_UserIdAndConsentItem_ConsentItemId(USER_ID, 1L))
                .thenReturn(Optional.of(existing));
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(required));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(existing));
        when(riskScoreRepository.findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                USER_ID, COMPANY_ID, LocalDate.now())).thenReturn(Optional.empty());

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 1L, false);

        assertFalse(response.isChecked());
        assertFalse(existing.isChecked());
    }

    @Test
    void toggleConsent_repeatedCallsWithSameExplicitChecked_areIdempotent() {
        // 같은 요청이 중복 전송돼도(네트워크 재시도 등) 반전 방식과 달리 몇 번을 다시 불러도
        // 항상 같은 최종 상태로 수렴해야 한다.
        ConsentItem required = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        UserConsentCheck existing = userConsentCheck(required, false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(1L)).thenReturn(Optional.of(required));
        when(userConsentCheckRepository.findByUser_UserIdAndConsentItem_ConsentItemId(USER_ID, 1L))
                .thenReturn(Optional.of(existing));
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(required));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(existing));
        when(riskScoreRepository.findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                USER_ID, COMPANY_ID, LocalDate.now())).thenReturn(Optional.empty());

        consentApiService.toggleConsent(USER_ID, 1L, true);
        ConsentPatchResponse second = consentApiService.toggleConsent(USER_ID, 1L, true);

        assertTrue(second.isChecked());
        assertTrue(existing.isChecked());
    }

    @Test
    void toggleConsent_createsNewCheckRecord_whenNoneExistsYet() {
        // 선택항목만 토글해서는 필수항목이 없어 계산 대상이 없다(personalRiskCalculator가 null
        // 반환) -> RiskScore 조회/저장이 아예 안 일어나므로, 이 테스트에선 그쪽은 목업하지 않는다.
        ConsentItem optional = consentItem(2L, ConsentItem.ItemType.OPTIONAL);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(2L)).thenReturn(Optional.of(optional));
        when(userConsentCheckRepository.findByUser_UserIdAndConsentItem_ConsentItemId(USER_ID, 2L))
                .thenReturn(Optional.empty());
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(optional));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of()); // 새로 만든 체크는 저장 목업이라 리포지토리엔 아직 없음

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 2L, null);

        // 기본값 false에서 반전되어 true(체크됨)여야 한다 — 신규 레코드 생성 케이스
        assertTrue(response.isChecked());
        ArgumentCaptor<UserConsentCheck> captor = ArgumentCaptor.forClass(UserConsentCheck.class);
        verify(userConsentCheckRepository).save(captor.capture());
        assertTrue(captor.getValue().isChecked());
        assertSame(optional, captor.getValue().getConsentItem());
    }

    @Test
    void toggleConsent_throwsIllegalArgumentException_whenUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> consentApiService.toggleConsent(USER_ID, 1L, null));
    }

    @Test
    void toggleConsent_throwsIllegalArgumentException_whenConsentItemNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> consentApiService.toggleConsent(USER_ID, 1L, null));
    }

    @Test
    void toggleConsent_returnsNullScoreAndGrade_ratherThanThrowing_whenConsentItemHasInvalidScoreData() {
        // 노가현님 리뷰 피드백 — PersonalRiskCalculator가 잘못된 점수값에 예외를 던지도록
        // 바뀌면서, 이걸 그대로 두면 데이터 오염된 기업 하나 때문에 그 기업 토글 요청이
        // 500으로 죽는다. toggleConsent()는 위험도 미상(null)으로 응답해야 한다.
        ConsentItem corrupted = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        corrupted.setDsScore(2); // 유효값(1,3,5) 아님
        UserConsentCheck existing = userConsentCheck(corrupted, false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(1L)).thenReturn(Optional.of(corrupted));
        when(userConsentCheckRepository.findByUser_UserIdAndConsentItem_ConsentItemId(USER_ID, 1L))
                .thenReturn(Optional.of(existing));
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(corrupted));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(existing));

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 1L, null);

        assertNull(response.getNewRiskScore());
        assertNull(response.getNewRiskGrade());
        verify(riskScoreRepository, never()).saveAndFlush(any());
    }

    @Test
    void toggleConsent_createsNewRepresentativeRiskScore_whenNoneExistsForToday() {
        ConsentItem required = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        UserConsentCheck existing = userConsentCheck(required, false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(1L)).thenReturn(Optional.of(required));
        when(userConsentCheckRepository.findByUser_UserIdAndConsentItem_ConsentItemId(USER_ID, 1L))
                .thenReturn(Optional.of(existing));
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(required));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(existing));
        when(riskScoreRepository.findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                USER_ID, COMPANY_ID, LocalDate.now())).thenReturn(Optional.empty());

        consentApiService.toggleConsent(USER_ID, 1L, null);

        ArgumentCaptor<RiskScore> captor = ArgumentCaptor.forClass(RiskScore.class);
        verify(riskScoreRepository).saveAndFlush(captor.capture());
        RiskScore saved = captor.getValue();
        assertTrue(saved.isRepresentative());
        assertEquals(LocalDate.now(), saved.getScoredAt());
        // 필수항목 하나(DS=1,ES=1,TF=1,PC=1.0,AI=1.0) -> 3.0(최솟값)
        assertEquals(0, BigDecimal.valueOf(3.0).compareTo(saved.getTotalScore()));
    }

    @Test
    void toggleConsent_updatesExistingRepresentativeRiskScore_insteadOfCreatingNew() {
        ConsentItem required = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        UserConsentCheck existing = userConsentCheck(required, false);
        RiskScore existingRep = new RiskScore();
        existingRep.setTotalScore(BigDecimal.valueOf(99.9)); // 갱신 전 값 — 새로 계산된 값으로 바뀌어야 함
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(1L)).thenReturn(Optional.of(required));
        when(userConsentCheckRepository.findByUser_UserIdAndConsentItem_ConsentItemId(USER_ID, 1L))
                .thenReturn(Optional.of(existing));
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(required));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(existing));
        when(riskScoreRepository.findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                USER_ID, COMPANY_ID, LocalDate.now())).thenReturn(Optional.of(existingRep));

        consentApiService.toggleConsent(USER_ID, 1L, null);

        ArgumentCaptor<RiskScore> captor = ArgumentCaptor.forClass(RiskScore.class);
        verify(riskScoreRepository).saveAndFlush(captor.capture());
        assertSame(existingRep, captor.getValue(), "새로 만들지 않고 기존 row를 그대로 갱신해야 한다");
        assertEquals(0, BigDecimal.valueOf(3.0).compareTo(existingRep.getTotalScore()));
    }

    @Test
    void toggleConsent_returnsNullScoreAndGrade_andSkipsRiskScoreSave_whenCompanyHasNoConsentItems() {
        ConsentItem orphanItem = consentItem(1L, ConsentItem.ItemType.OPTIONAL);
        UserConsentCheck existing = userConsentCheck(orphanItem, false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(1L)).thenReturn(Optional.of(orphanItem));
        when(userConsentCheckRepository.findByUser_UserIdAndConsentItem_ConsentItemId(USER_ID, 1L))
                .thenReturn(Optional.of(existing));
        // 비정상 케이스: 이 기업엔 동의 항목이 하나도 없다고 응답하도록 목업
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of());

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 1L, null);

        assertNull(response.getNewRiskScore());
        assertNull(response.getNewRiskGrade());
        verify(riskScoreRepository, never()).saveAndFlush(any());
    }

    @Test
    void toggleConsent_reflectsJustToggledState_inTheSameCallsRiskCalculation() {
        // 필수항목(DS=1 등 최소값) + 선택항목(DS=5,ES=3,TF=3,PC=1.5,AI=1.5, 아직 미체크)
        ConsentItem required = consentItem(1L, ConsentItem.ItemType.REQUIRED);
        ConsentItem optional = consentItem(2L, ConsentItem.ItemType.OPTIONAL);
        optional.setDsScore(5);
        optional.setEsScore(3);
        optional.setTfScore(3);
        optional.setPcScore(1.5);
        optional.setAiScore(1.5);
        UserConsentCheck check = userConsentCheck(optional, false); // 토글 전: 미체크

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(2L)).thenReturn(Optional.of(optional));
        when(userConsentCheckRepository.findByUser_UserIdAndConsentItem_ConsentItemId(USER_ID, 2L))
                .thenReturn(Optional.of(check));
        when(consentItemRepository.findByCompany_CompanyId(COMPANY_ID)).thenReturn(List.of(required, optional));
        // 재계산 시점엔 이미 이 check 객체가 토글된 뒤이므로(같은 트랜잭션), 목업이 같은 참조를 돌려주면
        // isChecked()가 true로 바뀐 상태가 그대로 반영된다.
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, COMPANY_ID))
                .thenReturn(List.of(check));
        when(riskScoreRepository.findByUser_UserIdAndCompany_CompanyIdAndScoredAtAndIsRepresentativeTrue(
                USER_ID, COMPANY_ID, LocalDate.now())).thenReturn(Optional.empty());

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 2L, null);

        assertTrue(response.isChecked());
        // 선택항목까지 반영된 최댓값(45.5)이 응답에 나와야 한다 — 필수항목만의 3.0이 아니라
        assertEquals(0, BigDecimal.valueOf(45.5).compareTo(response.getNewRiskScore()));
    }

    // ---- getCompaniesSortedByRisk ----

    @Test
    void getCompaniesSortedByRisk_sortsCompaniesByDescendingPersonalRiskScore() {
        Company lowRiskCompany = company(100L, "안전기업");
        Company highRiskCompany = company(200L, "위험기업");
        ConsentItem lowRiskItem = consentItemForCompany(1L, lowRiskCompany, ConsentItem.ItemType.REQUIRED,
                1, 1, 1, 1.0, 1.0); // 최솟값 3.0
        ConsentItem highRiskItem = consentItemForCompany(2L, highRiskCompany, ConsentItem.ItemType.REQUIRED,
                5, 3, 3, 1.5, 1.5); // 최댓값 45.5

        when(companyRepository.findAll()).thenReturn(List.of(lowRiskCompany, highRiskCompany));
        when(consentItemRepository.findByCompany_CompanyId(100L)).thenReturn(List.of(lowRiskItem));
        when(consentItemRepository.findByCompany_CompanyId(200L)).thenReturn(List.of(highRiskItem));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, 100L))
                .thenReturn(List.of());
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, 200L))
                .thenReturn(List.of());

        List<CompanyRiskResponse> result = consentApiService.getCompaniesSortedByRisk(USER_ID);

        assertEquals(2, result.size());
        assertEquals(200L, result.get(0).getCompanyId(), "위험도가 높은 기업이 먼저 나와야 한다");
        assertEquals(100L, result.get(1).getCompanyId());
    }

    @Test
    void getCompaniesSortedByRisk_sortsCompanyWithNoConsentItemsLast() {
        Company noItemsCompany = company(300L, "동의항목없는기업");
        Company normalCompany = company(100L, "정상기업");
        ConsentItem item = consentItemForCompany(1L, normalCompany, ConsentItem.ItemType.REQUIRED,
                1, 1, 1, 1.0, 1.0); // 3.0 (양수 최솟값)

        when(companyRepository.findAll()).thenReturn(List.of(noItemsCompany, normalCompany));
        when(consentItemRepository.findByCompany_CompanyId(300L)).thenReturn(List.of()); // 동의항목 자체가 없음
        when(consentItemRepository.findByCompany_CompanyId(100L)).thenReturn(List.of(item));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, 100L))
                .thenReturn(List.of());

        List<CompanyRiskResponse> result = consentApiService.getCompaniesSortedByRisk(USER_ID);

        assertEquals(100L, result.get(0).getCompanyId(), "점수가 있는 기업이 먼저 나와야 한다");
        assertEquals(300L, result.get(1).getCompanyId(), "위험도를 계산할 수 없는 기업(null)은 맨 뒤로 밀려야 한다");
        assertNull(result.get(1).getRiskScore());
        assertNull(result.get(1).getRiskGrade());
    }

    @Test
    void getCompaniesSortedByRisk_returnsEmptyList_whenNoCompaniesExist() {
        when(companyRepository.findAll()).thenReturn(List.of());

        List<CompanyRiskResponse> result = consentApiService.getCompaniesSortedByRisk(USER_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void getCompaniesSortedByRisk_mapsCompanyFieldsAndComputedRisk() {
        Company company = company(100L, "테스트기업");
        company.setPackageName("com.test.app");
        company.setPrivacyUrl("https://test.com/privacy");
        company.setIsmsCertified(true);
        // DS=5, ES=1,TF=1,PC=1.0,AI=1.0 -> 5 + (1*1*1*1)*2 = 7.0 -> LOW
        ConsentItem item = consentItemForCompany(1L, company, ConsentItem.ItemType.REQUIRED,
                5, 1, 1, 1.0, 1.0);
        LocalDateTime crawledAt = LocalDateTime.of(2026, 8, 20, 3, 0, 0);
        PolicySnapshot snapshot = new PolicySnapshot();
        snapshot.setCrawledAt(crawledAt);

        when(companyRepository.findAll()).thenReturn(List.of(company));
        when(consentItemRepository.findByCompany_CompanyId(100L)).thenReturn(List.of(item));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, 100L))
                .thenReturn(List.of());
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(100L))
                .thenReturn(Optional.of(snapshot));

        CompanyRiskResponse response = consentApiService.getCompaniesSortedByRisk(USER_ID).get(0);

        assertEquals(100L, response.getCompanyId());
        assertEquals("테스트기업", response.getCompanyName());
        assertEquals("com.test.app", response.getPackageName());
        assertEquals("https://test.com/privacy", response.getPrivacyUrl());
        assertTrue(response.isIsmsCertified());
        assertEquals(0, BigDecimal.valueOf(7.0).compareTo(response.getRiskScore()));
        assertEquals("LOW", response.getRiskGrade());
        assertEquals(crawledAt, response.getCrawledAt());
    }

    @Test
    void getCompaniesSortedByRisk_returnsNullCrawledAt_whenCompanyHasNoSnapshotYet() {
        // 기업은 등록됐지만 아직 최초 크롤링 전(PolicySnapshot 없음)인 경우
        Company company = company(100L, "수집전기업");

        when(companyRepository.findAll()).thenReturn(List.of(company));
        when(consentItemRepository.findByCompany_CompanyId(100L)).thenReturn(List.of());
        when(policySnapshotRepository.findFirstByCompany_CompanyIdOrderByCrawledAtDesc(100L))
                .thenReturn(Optional.empty());

        CompanyRiskResponse response = consentApiService.getCompaniesSortedByRisk(USER_ID).get(0);

        assertNull(response.getCrawledAt());
    }

    @Test
    void getCompaniesSortedByRisk_skipsCompanyWithInvalidScoreData_ratherThanFailingWholeList() {
        // 노가현님 리뷰 피드백 — 기업 하나의 동의항목 데이터가 잘못돼도(예: DS=2, 유효값 아님)
        // 목록 전체 API가 500으로 죽으면 안 되고, 그 기업만 위험도 미상(null)으로 나오고
        // 나머지 기업은 정상 응답해야 한다.
        Company corruptedCompany = company(400L, "데이터오염기업");
        Company normalCompany = company(100L, "정상기업");
        ConsentItem corruptedItem = consentItemForCompany(1L, corruptedCompany, ConsentItem.ItemType.REQUIRED,
                2, 1, 1, 1.0, 1.0); // DS=2는 유효값(1,3,5) 아님
        ConsentItem normalItem = consentItemForCompany(2L, normalCompany, ConsentItem.ItemType.REQUIRED,
                1, 1, 1, 1.0, 1.0); // 3.0

        when(companyRepository.findAll()).thenReturn(List.of(corruptedCompany, normalCompany));
        when(consentItemRepository.findByCompany_CompanyId(400L)).thenReturn(List.of(corruptedItem));
        when(consentItemRepository.findByCompany_CompanyId(100L)).thenReturn(List.of(normalItem));
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, 400L))
                .thenReturn(List.of());
        when(userConsentCheckRepository.findAllByUser_UserIdAndConsentItem_Company_CompanyId(USER_ID, 100L))
                .thenReturn(List.of());

        List<CompanyRiskResponse> result = consentApiService.getCompaniesSortedByRisk(USER_ID);

        assertEquals(2, result.size(), "예외를 던진 기업이 있어도 목록 전체가 죽지 않고 둘 다 나와야 한다");
        CompanyRiskResponse corrupted = result.stream()
                .filter(r -> r.getCompanyId().equals(400L)).findFirst().orElseThrow();
        CompanyRiskResponse normal = result.stream()
                .filter(r -> r.getCompanyId().equals(100L)).findFirst().orElseThrow();
        assertNull(corrupted.getRiskScore(), "데이터 오염된 기업은 위험도 미상(null)으로 나와야 한다");
        assertNull(corrupted.getRiskGrade());
        assertEquals(0, BigDecimal.valueOf(3.0).compareTo(normal.getRiskScore()), "다른 정상 기업은 영향받지 않아야 한다");
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        return user;
    }

    private Company company(Long id, String name) {
        Company company = new Company();
        company.setCompanyId(id);
        company.setCompanyName(name);
        company.setPackageName("com.example." + id);
        company.setPrivacyUrl("https://example.com/" + id);
        return company;
    }

    private ConsentItem consentItemForCompany(Long id, Company company, ConsentItem.ItemType type,
                                               int ds, int es, int tf, double pc, double ai) {
        ConsentItem item = new ConsentItem();
        item.setConsentItemId(id);
        item.setItemType(type);
        item.setItemName("항목" + id);
        item.setDsScore(ds);
        item.setEsScore(es);
        item.setTfScore(tf);
        item.setPcScore(pc);
        item.setAiScore(ai);
        item.setCompany(company);
        return item;
    }

    private ConsentItem consentItem(Long id, ConsentItem.ItemType type) {
        ConsentItem item = new ConsentItem();
        item.setConsentItemId(id);
        item.setItemType(type);
        item.setItemName("항목" + id);
        item.setDsScore(1);
        item.setEsScore(1);
        item.setTfScore(1);
        item.setPcScore(1.0);
        item.setAiScore(1.0);
        Company company = new Company();
        company.setCompanyId(COMPANY_ID);
        item.setCompany(company);
        return item;
    }

    private UserConsentCheck userConsentCheck(ConsentItem item, boolean checked) {
        UserConsentCheck check = new UserConsentCheck();
        User user = new User();
        check.setUser(user);
        check.setConsentItem(item);
        check.setChecked(checked);
        return check;
    }
}
