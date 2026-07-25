package com.consentradar.consentradar.api;

import com.consentradar.consentradar.api.dto.CompanyRiskResponse;
import com.consentradar.consentradar.api.dto.ConsentItemResponse;
import com.consentradar.consentradar.api.dto.ConsentPatchResponse;
import com.consentradar.consentradar.entity.*;
import com.consentradar.consentradar.repository.*;
import com.dynamicconsent.model.RiskResult;
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
    private final PersonalRiskCalculator personalRiskCalculator;

    public ConsentApiService(UserRepository userRepository,
                             ConsentItemRepository consentItemRepository,
                             UserConsentCheckRepository userConsentCheckRepository,
                             CompanyRepository companyRepository,
                             RiskScoreRepository riskScoreRepository,
                             PersonalRiskCalculator personalRiskCalculator) {
        this.userRepository              = userRepository;
        this.consentItemRepository       = consentItemRepository;
        this.userConsentCheckRepository  = userConsentCheckRepository;
        this.companyRepository           = companyRepository;
        this.riskScoreRepository         = riskScoreRepository;
        this.personalRiskCalculator      = personalRiskCalculator;
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
     * [수정 이력 — 동시 토글 경합 조건 대응, Sprint04 엣지케이스 검증]
     * 같은 기업의 선택동의 여러 개를 짧은 시간 안에 토글하면(예: 스위치 두 개를 빠르게
     * 연속 조작) 동시 요청끼리 대표 RiskScore row를 두고 경합한다.
     *   1. 오늘자 대표 row가 아직 없을 때 두 요청이 동시에 "없음"으로 보고 각자 새로
     *      insert하면, RiskScore의 유니크 제약(user_id, company_id, scored_at,
     *      is_representative)에 하나가 위반되어 실패한다.
     *   2. 이미 row가 있을 때 두 요청이 동시에 읽고 수정하면 나중에 커밋한 쪽이 먼저
     *      커밋된 값을 조용히 덮어쓴다(lost update).
     * RiskScore.version(@Version, 낙관적 락)으로 이 경합을 예외로 드러내고, 체크 토글부터
     * 위험도 재계산·저장까지 전체를 하나의 트랜잭션으로 묶어 재시도한다 — 재계산까지
     * 통째로 재시도해야, 재시도 시점에 이미 커밋된 상대방의 토글까지 반영한 최종값이
     * 저장된다. (재시도는 이 메서드를 다시 호출하는 쪽인 {@link com.consentradar.consentradar.api.ConsentApiController}가
     * 담당한다 — 같은 트랜잭션 안에서 실패 후 재시도하면 세션이 오염되어 불가능하고,
     * 이 메서드 자체는 프록시를 통해 매번 새 트랜잭션으로 호출돼야 하기 때문이다.)
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
        RiskResult newResult = personalRiskCalculator.calculate(userId, company.getCompanyId());

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
            // saveAndFlush로 유니크 제약/낙관적 락 위반을 이 메서드 안에서 즉시 드러낸다.
            riskScoreRepository.saveAndFlush(rep);
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
                    RiskResult personalResult = personalRiskCalculator.calculate(userId, company.getCompanyId());
                    return new CompanyRiskResponse(company, personalResult);
                })
                .sorted(Comparator.comparing(
                        r -> r.getRiskScore() != null ? r.getRiskScore() : BigDecimal.ZERO,
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    /**
     * GET /companies/{companyId}/consent-items?userId=
     * 기업의 필수+선택 동의 항목 전체를 5대 변수 값과 함께 반환한다. REQUIRED는 항상
     * checked=true, OPTIONAL은 이 사용자의 실제 체크 여부를 반영한다.
     *
     * 동의 세부사항 탭(4-5)이 지금까지 프론트 mock 데이터로만 구현돼 있던 것을 실제
     * 데이터로 연동하기 위해 추가했다. 응답의 consentItemId를 그대로 PATCH
     * /users/{userId}/consents/{consentItemId} 호출에 사용하면 된다.
     */
    @Transactional(readOnly = true)
    public List<ConsentItemResponse> getConsentItems(Long userId, Long companyId) {
        List<ConsentItem> items = consentItemRepository.findByCompany_CompanyId(companyId);
        Set<Long> checkedOptionalItemIds = personalRiskCalculator.findCheckedOptionalItemIds(userId, companyId);

        return items.stream()
                .map(item -> new ConsentItemResponse(
                        item,
                        item.getItemType() == ConsentItem.ItemType.REQUIRED
                                || checkedOptionalItemIds.contains(item.getConsentItemId())))
                .collect(Collectors.toList());
    }
}
