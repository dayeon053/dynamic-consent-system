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
     * 동의 상태를 토글하고, 해당 기업의 위험도를 재산출해 반환한다.
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

        // 해당 기업의 모든 ConsentItem으로 위험도 재산출
        Company company = consentItem.getCompany();
        List<ConsentItem> allItems = consentItemRepository.findByCompany_CompanyId(company.getCompanyId());
        List<RiskInput> inputs = allItems.stream()
                .map(this::toRiskInput)
                .collect(Collectors.toList());

        RiskResult newResult = inputs.isEmpty()
                ? null
                : RiskCalculator.calculateMax(inputs);

        // 기업 대표 RiskScore 갱신
        if (newResult != null) {
            RiskScore rep = riskScoreRepository
                    .findTopByCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtDesc(company.getCompanyId())
                    .orElseGet(() -> {
                        RiskScore r = new RiskScore();
                        r.setCompany(company);
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
     * 기업 목록을 대표 위험도 기준 내림차순으로 반환한다.
     */
    @Transactional(readOnly = true)
    public List<CompanyRiskResponse> getCompaniesSortedByRisk(Long userId) {
        List<Company> companies = companyRepository.findAll();

        return companies.stream()
                .map(company -> {
                    RiskScore rep = riskScoreRepository
                            .findTopByCompany_CompanyIdAndIsRepresentativeTrueOrderByScoredAtDesc(company.getCompanyId())
                            .orElse(null);
                    return new CompanyRiskResponse(company, rep);
                })
                .sorted(Comparator.comparing(
                        r -> r.getRiskScore() != null ? r.getRiskScore() : BigDecimal.ZERO,
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());
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
