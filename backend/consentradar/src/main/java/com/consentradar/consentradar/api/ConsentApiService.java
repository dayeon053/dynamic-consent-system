package com.consentradar.consentradar.api;

import com.consentradar.consentradar.api.dto.CompanyRiskResponse;
import com.consentradar.consentradar.api.dto.ConsentPatchResponse;
import com.consentradar.consentradar.entity.*;
import com.consentradar.consentradar.repository.*;
import com.dynamicconsent.algorithm.RiskCalculator;
import com.dynamicconsent.model.RiskInput;
import com.dynamicconsent.model.RiskResult;
import com.dynamicconsent.model.variable.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConsentApiService {

    private final UserRepository userRepository;
    private final ConsentItemRepository consentItemRepository;
    private final UserConsentCheckRepository userConsentCheckRepository;
    private final CompanyRepository companyRepository;
    private final RiskScoreRepository riskScoreRepository;

    public ConsentApiService(UserRepository userRepository,
                             ConsentItemRepository consentItemRepository,
                             UserConsentCheckRepository userConsentCheckRepository,
                             CompanyRepository companyRepository,
                             RiskScoreRepository riskScoreRepository) {
        this.userRepository              = userRepository;
        this.consentItemRepository       = consentItemRepository;
        this.userConsentCheckRepository  = userConsentCheckRepository;
        this.companyRepository           = companyRepository;
        this.riskScoreRepository         = riskScoreRepository;
    }

    /**
     * PATCH /users/{userId}/consents/{consentItemId}
     * 동의 상태를 토글하고, "이 사용자" 기준 개인 맞춤 위험도(필수동의 + 사용자가 실제
     * 체크한 선택동의)를 재산출해 반환한다.
     *
     * [수정 이력 — 개인 맞춤 필터링 버그 수정]
     * 기존에는 토글 상태와 무관하게 기업의 ConsentItem 전체(필수+선택, 체크여부 무관)로
     * 위험도를 재계산해서, 사용자가 뭘 체크/해제하든 항상 같은 점수(사실상 워스트 케이스)가
     * 나오는 문제가 있었다. 유즈케이스 문서(F2)는 "워스트 케이스는 보여주지 않고 개인 맞춤
     * 위험도만 보여준다"고 명시하고 있어서, 이 메서드가 실제로 UC-03/2-2/2-5를 구현하도록
     * REQUIRED 항목 + 이 사용자가 isChecked=true로 체크한 OPTIONAL 항목만으로 다시 계산한다.
     *
     * 저장할 때도 기존 코드는 배치 파이프라인이 쓰는 company 단위 대표 RiskScore(user=null)를
     * 그대로 덮어써서, 한 사용자가 토글할 때마다 다른 모든 사용자에게 보이는 값까지 같이
     * 바뀌는 문제가 있었다. RiskScore에 user 컬럼이 이미 있으므로(회원별 row 구분 가능),
     * company+user 조합의 row를 찾아서 갱신/생성하도록 바꿨다. 배치가 쓰는 user=null row는
     * 건드리지 않는다.
     */
    @Transactional
    public ConsentPatchResponse toggleConsent(Long userId, Long consentItemId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        ConsentItem consentItem = consentItemRepository.findById(consentItemId)
                .orElseThrow(() -> new IllegalArgumentException("동의항목을 찾을 수 없습니다: " + consentItemId));

        // 기존 체크 레코드가 있으면 토글, 없으면 신규 생성
        UserConsentCheck check = userConsentCheckRepository
                .findByUser_UserIdAndConsentItem_ConsentItemId(userId, consentItemId)
                .orElseGet(() -> {
                    UserConsentCheck c = new UserConsentCheck();
                    c.setUser(user);
                    c.setConsentItem(consentItem);
                    return c;
                });

        check.setChecked(!check.isChecked());
        userConsentCheckRepository.save(check);

        Company company = consentItem.getCompany();
        RiskResult newResult = calculatePersonalRisk(userId, company.getCompanyId());

        // 사용자별 개인 맞춤 대표 RiskScore 갱신 (company+user 로 구분되는 row. 배치용 user=null row와 별개)
        if (newResult != null) {
            RiskScore rep = riskScoreRepository
                    .findTopByCompany_CompanyIdAndUser_UserIdAndIsRepresentativeTrueOrderByScoredAtDesc(
                            company.getCompanyId(), userId)
                    .orElseGet(() -> {
                        RiskScore r = new RiskScore();
                        r.setCompany(company);
                        r.setUser(user);
                        r.setRepresentative(true);
                        return r;
                    });
            rep.setTotalScore(BigDecimal.valueOf(newResult.getScore()));
            rep.setGrade(RiskScore.Grade.valueOf(newResult.getGrade().name()));
            rep.setScoredAt(LocalDate.now());
            riskScoreRepository.save(rep);
        }

        BigDecimal newScore = newResult != null ? BigDecimal.valueOf(newResult.getScore()) : null;
        String newGrade     = newResult != null ? newResult.getGrade().name() : null;

        return new ConsentPatchResponse(consentItemId, check.isChecked(), newScore, newGrade);
    }

    /**
     * GET /companies?userId=&sort=risk_score_desc
     * 기업 목록을 "이 사용자"의 개인 맞춤 위험도 기준 내림차순으로 반환한다.
     *
     * [수정 이력] 기존에는 userId 파라미터를 받고도 실제로는 쓰지 않고, 배치 파이프라인이
     * 저장한 company 단위 대표 점수(user=null, 전체 항목 기준)를 모든 사용자에게 동일하게
     * 내려줬다. 위험기관리스트는 "개인 맞춤 위험도 기준"으로 정렬돼야 한다는 문서(F4) 요구를
     * 충족하지 못했던 부분이라, 사용자별로 실시간 계산하도록 바꿨다.
     */
    @Transactional(readOnly = true)
    public List<CompanyRiskResponse> getCompaniesSortedByRisk(Long userId) {
        List<Company> companies = companyRepository.findAll();

        return companies.stream()
                .map(company -> {
                    RiskResult personalResult = calculatePersonalRisk(userId, company.getCompanyId());
                    return new CompanyRiskResponse(company, personalResult);
                })
                .sorted(Comparator.comparing(
                        r -> r.getRiskScore() != null ? r.getRiskScore() : BigDecimal.ZERO,
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    /**
     * 필수동의 전체 + 이 사용자가 isChecked=true로 체크한 선택동의만으로 위험도를 계산한다.
     * (F2: "필수동의 + 사용자가 실제 체크한 선택동의를 기준으로 산출", 워스트 케이스 아님)
     *
     * 대표값 산출 방식은 기존 calculateMax(항목별 점수의 최댓값)를 그대로 유지했다.
     * FE의 combineImpacts(변수별 최댓값 합성)와 다른 방식이라 다인원 항목 조합에서 최종
     * 숫자가 달라질 수 있다는 점은 docs/personal_risk_server_decision.md 에 이미 정리돼 있고,
     * 그 부분은 별도 팀 결정 사항이라 이번 수정 범위에서는 건드리지 않았다.
     */
    private RiskResult calculatePersonalRisk(Long userId, Long companyId) {
        List<ConsentItem> allItems = consentItemRepository.findByCompany_CompanyId(companyId);
        if (allItems.isEmpty()) {
            return null;
        }

        Set<Long> checkedOptionalItemIds = userConsentCheckRepository
                .findAllByUser_UserIdAndConsentItem_Company_CompanyId(userId, companyId)
                .stream()
                .filter(UserConsentCheck::isChecked)
                .map(c -> c.getConsentItem().getConsentItemId())
                .collect(Collectors.toSet());

        List<RiskInput> personalInputs = allItems.stream()
                .filter(item -> item.getItemType() == ConsentItem.ItemType.REQUIRED
                        || checkedOptionalItemIds.contains(item.getConsentItemId()))
                .map(this::toRiskInput)
                .collect(Collectors.toList());

        // 필수동의조차 없는 비정상 데이터인 경우를 대비한 방어 코드
        return personalInputs.isEmpty() ? null : RiskCalculator.calculateMax(personalInputs);
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

    private DataSensitivity scoreToDataSensitivity(int score) {
        return switch (score) {
            case 1 -> DataSensitivity.LOW;
            case 3 -> DataSensitivity.MODERATE;
            default -> DataSensitivity.HIGH;
        };
    }

    private ExposureScope scoreToExposureScope(int score) {
        return switch (score) {
            case 1 -> ExposureScope.LOW;
            case 2 -> ExposureScope.MEDIUM;
            default -> ExposureScope.HIGH;
        };
    }

    private TimeFactor scoreToTimeFactor(int score) {
        return switch (score) {
            case 1 -> TimeFactor.SHORT;
            case 2 -> TimeFactor.MEDIUM;
            default -> TimeFactor.LONG;
        };
    }

    private PurposeClarity scoreToPurposeClarity(double score) {
        return score <= 1.0 ? PurposeClarity.COMPLIANT : PurposeClarity.NON_COMPLIANT;
    }

    private AiRiskFactor scoreToAiRiskFactor(double score) {
        return score <= 1.0 ? AiRiskFactor.LOW_RISK : AiRiskFactor.HIGH_RISK;
    }
}
