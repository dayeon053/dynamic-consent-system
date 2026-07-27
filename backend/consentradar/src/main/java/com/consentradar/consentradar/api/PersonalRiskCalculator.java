package com.consentradar.consentradar.api;

import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.entity.UserConsentCheck;
import com.consentradar.consentradar.repository.ConsentItemRepository;
import com.consentradar.consentradar.repository.UserConsentCheckRepository;
import com.dynamicconsent.algorithm.RiskCalculator;
import com.dynamicconsent.model.RiskInput;
import com.dynamicconsent.model.RiskResult;
import com.dynamicconsent.model.variable.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "필수동의 전체 + 이 사용자가 실제 체크한 선택동의"만으로 개인 맞춤 위험도를 계산한다.
 * (F2: 워스트 케이스가 아닌 개인 맞춤 위험도 산출)
 *
 * {@link ConsentApiService}(읽기 경로: GET /companies, GET consent-items)와
 * {@link RiskScoreUpsertService}(쓰기 경로: PATCH 토글 시 대표 RiskScore 갱신)가
 * 매번 같은 계산 로직을 공유하도록 분리했다 — 특히 쓰기 경로는 동시성 재시도 시마다
 * 이 계산을 다시 수행해 최신 커밋 상태를 반영해야 하므로 별도 컴포넌트로 둔다.
 */
@Component
public class PersonalRiskCalculator {

    private final ConsentItemRepository consentItemRepository;
    private final UserConsentCheckRepository userConsentCheckRepository;

    public PersonalRiskCalculator(ConsentItemRepository consentItemRepository,
                                   UserConsentCheckRepository userConsentCheckRepository) {
        this.consentItemRepository = consentItemRepository;
        this.userConsentCheckRepository = userConsentCheckRepository;
    }

    // TODO(PR #30 머지 후 처리): 개인 맞춤 위험도 정본 합산 방식이 combineImpacts로
    // 통일되기로 팀 결정됨(docs/personal_risk_server_decision.md). PR #30
    // (feature/combine-impacts-canonical-model)이 develop에 머지되면 아래
    // calculateMax를 combineImpacts로 교체할 것.
    public RiskResult calculate(Long userId, Long companyId) {
        List<ConsentItem> allItems = consentItemRepository.findByCompany_CompanyId(companyId);
        if (allItems.isEmpty()) {
            return null;
        }

        Set<Long> checkedOptionalItemIds = findCheckedOptionalItemIds(userId, companyId);

        List<RiskInput> personalInputs = allItems.stream()
                .filter(item -> item.getItemType() == ConsentItem.ItemType.REQUIRED
                        || checkedOptionalItemIds.contains(item.getConsentItemId()))
                .map(this::toRiskInput)
                .collect(Collectors.toList());

        // 필수동의조차 없는 비정상 데이터인 경우를 대비한 방어 코드
        return personalInputs.isEmpty() ? null : RiskCalculator.calculateMax(personalInputs);
    }

    public Set<Long> findCheckedOptionalItemIds(Long userId, Long companyId) {
        return userConsentCheckRepository
                .findAllByUser_UserIdAndConsentItem_Company_CompanyId(userId, companyId)
                .stream()
                .filter(UserConsentCheck::isChecked)
                .map(c -> c.getConsentItem().getConsentItemId())
                .collect(Collectors.toSet());
    }

    private RiskInput toRiskInput(ConsentItem item) {
        return new RiskInput(
                scoreToDataSensitivity(item.getDsScore()),
                scoreToExposureScope(item.getEsScore()),
                scoreToTimeFactor(item.getTfScore()),
                scoreToPurposeClarity(item.getPcScore()),
                scoreToAiRiskFactor(item.getAiScore())
        );
    }

    /**
     * [수정 이력 — default 뭉개기 버그 수정]
     * 기존엔 1,3 이외의 모든 값(잘못된 DB 값 포함)이 default로 떨어져 조용히 HIGH(5점)로
     * 매핑됐다. DS 유효값은 {@link DataSensitivity} 문서대로 1/3/5뿐이므로 전부 명시하고,
     * 그 외 값은 데이터 오염을 조용히 삼키지 않도록 예외를 던진다.
     */
    private DataSensitivity scoreToDataSensitivity(int score) {
        return switch (score) {
            case 1 -> DataSensitivity.LOW;
            case 3 -> DataSensitivity.MODERATE;
            case 5 -> DataSensitivity.HIGH;
            default -> throw new IllegalArgumentException(
                    "유효하지 않은 DS(데이터 민감도) 점수입니다: " + score + " (허용값: 1, 3, 5)");
        };
    }

    private ExposureScope scoreToExposureScope(int score) {
        return switch (score) {
            case 1 -> ExposureScope.LOW;
            case 2 -> ExposureScope.MEDIUM;
            case 3 -> ExposureScope.HIGH;
            default -> throw new IllegalArgumentException(
                    "유효하지 않은 ES(노출 범위) 점수입니다: " + score + " (허용값: 1, 2, 3)");
        };
    }

    private TimeFactor scoreToTimeFactor(int score) {
        return switch (score) {
            case 1 -> TimeFactor.SHORT;
            case 2 -> TimeFactor.MEDIUM;
            case 3 -> TimeFactor.LONG;
            default -> throw new IllegalArgumentException(
                    "유효하지 않은 TF(경과 시간) 점수입니다: " + score + " (허용값: 1, 2, 3)");
        };
    }

    private PurposeClarity scoreToPurposeClarity(double score) {
        return score <= 1.0 ? PurposeClarity.COMPLIANT : PurposeClarity.NON_COMPLIANT;
    }

    private AiRiskFactor scoreToAiRiskFactor(double score) {
        return score <= 1.0 ? AiRiskFactor.LOW_RISK : AiRiskFactor.HIGH_RISK;
    }
}
