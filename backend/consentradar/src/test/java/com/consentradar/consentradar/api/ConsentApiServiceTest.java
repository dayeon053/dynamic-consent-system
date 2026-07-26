package com.consentradar.consentradar.api;

import com.consentradar.consentradar.api.dto.ConsentItemResponse;
import com.consentradar.consentradar.api.dto.ConsentPatchResponse;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.entity.RiskScore;
import com.consentradar.consentradar.entity.User;
import com.consentradar.consentradar.entity.UserConsentCheck;
import com.consentradar.consentradar.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    @Mock private RiskScoreRepository riskScoreRepository;

    private ConsentApiService consentApiService;

    private static final Long USER_ID = 1L;
    private static final Long COMPANY_ID = 10L;

    @BeforeEach
    void setUp() {
        // PersonalRiskCalculator는 실제 인스턴스를 써서 mock 리포지토리 스텁이 그대로 통하게 한다
        // (getConsentItems 등이 내부적으로 이 계산기에 위임하기 때문).
        PersonalRiskCalculator personalRiskCalculator =
                new PersonalRiskCalculator(consentItemRepository, userConsentCheckRepository);
        consentApiService = new ConsentApiService(
                userRepository, consentItemRepository, userConsentCheckRepository,
                companyRepository, riskScoreRepository, personalRiskCalculator);
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
        when(riskScoreRepository.findTopByCompany_CompanyIdAndUser_UserIdAndIsRepresentativeTrueOrderByScoredAtDesc(
                COMPANY_ID, USER_ID)).thenReturn(Optional.empty());

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 1L);

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
        when(riskScoreRepository.findTopByCompany_CompanyIdAndUser_UserIdAndIsRepresentativeTrueOrderByScoredAtDesc(
                COMPANY_ID, USER_ID)).thenReturn(Optional.empty());

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 1L);

        assertFalse(response.isChecked());
        assertFalse(existing.isChecked());
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

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 2L);

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

        assertThrows(IllegalArgumentException.class, () -> consentApiService.toggleConsent(USER_ID, 1L));
    }

    @Test
    void toggleConsent_throwsIllegalArgumentException_whenConsentItemNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(consentItemRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> consentApiService.toggleConsent(USER_ID, 1L));
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
        when(riskScoreRepository.findTopByCompany_CompanyIdAndUser_UserIdAndIsRepresentativeTrueOrderByScoredAtDesc(
                COMPANY_ID, USER_ID)).thenReturn(Optional.empty());

        consentApiService.toggleConsent(USER_ID, 1L);

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
        when(riskScoreRepository.findTopByCompany_CompanyIdAndUser_UserIdAndIsRepresentativeTrueOrderByScoredAtDesc(
                COMPANY_ID, USER_ID)).thenReturn(Optional.of(existingRep));

        consentApiService.toggleConsent(USER_ID, 1L);

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

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 1L);

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
        when(riskScoreRepository.findTopByCompany_CompanyIdAndUser_UserIdAndIsRepresentativeTrueOrderByScoredAtDesc(
                COMPANY_ID, USER_ID)).thenReturn(Optional.empty());

        ConsentPatchResponse response = consentApiService.toggleConsent(USER_ID, 2L);

        assertTrue(response.isChecked());
        // 선택항목까지 반영된 최댓값(45.5)이 응답에 나와야 한다 — 필수항목만의 3.0이 아니라
        assertEquals(0, BigDecimal.valueOf(45.5).compareTo(response.getNewRiskScore()));
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        return user;
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
