package com.consentradar.consentradar.api;

import com.consentradar.consentradar.api.dto.CompanyRiskResponse;
import com.consentradar.consentradar.api.dto.ConsentItemResponse;
import com.consentradar.consentradar.api.dto.ConsentPatchResponse;
import com.consentradar.consentradar.consenthistory.UserConsentHistoryRecorder;
import com.consentradar.consentradar.entity.*;
import com.consentradar.consentradar.repository.*;
import com.consentradar.consentradar.riskhistory.PersonalRiskHistoryService;
import com.dynamicconsent.model.RiskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConsentApiService {

    private static final Logger log = LoggerFactory.getLogger(ConsentApiService.class);

    private final UserRepository userRepository;
    private final ConsentItemRepository consentItemRepository;
    private final UserConsentCheckRepository userConsentCheckRepository;
    private final CompanyRepository companyRepository;
    private final PersonalRiskCalculator personalRiskCalculator;
    private final UserConsentHistoryRecorder userConsentHistoryRecorder;
    private final PersonalRiskHistoryService personalRiskHistoryService;

    public ConsentApiService(UserRepository userRepository,
                             ConsentItemRepository consentItemRepository,
                             UserConsentCheckRepository userConsentCheckRepository,
                             CompanyRepository companyRepository,
                             PersonalRiskCalculator personalRiskCalculator,
                             UserConsentHistoryRecorder userConsentHistoryRecorder,
                             PersonalRiskHistoryService personalRiskHistoryService) {
        this.userRepository              = userRepository;
        this.consentItemRepository       = consentItemRepository;
        this.userConsentCheckRepository  = userConsentCheckRepository;
        this.companyRepository           = companyRepository;
        this.personalRiskCalculator      = personalRiskCalculator;
        this.userConsentHistoryRecorder  = userConsentHistoryRecorder;
        this.personalRiskHistoryService  = personalRiskHistoryService;
    }

    /**
     * PATCH /users/{userId}/consents/{consentItemId}
     * 동의 상태를 요청된 값으로 설정(레거시 호출은 토글)하고, "이 사용자" 기준 개인 맞춤
     * 위험도(필수동의 + 사용자가 실제 체크한 선택동의)를 재산출해 반환한다.
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
     *
     * [수정 이력 — 동의 변경 이력 조회 API 추가]
     * UserConsentCheck는 "현재 상태"만 덮어써 보관해 이력이 남지 않으므로, 상태를 바꿀 때마다
     * {@link UserConsentHistoryRecorder}로 별도 이력 테이블에 한 건씩 append한다. 개인 맞춤
     * 위험도 계산은 여전히 UserConsentCheck(현재 상태)만 조회하는 기존 구조 그대로다.
     *
     * [수정 이력 — PATCH 멱등성 개선(A안)]
     * {@code desiredChecked}가 주어지면(요청 본문 {@code {"checked": true/false}}) 그 값을
     * 그대로 저장한다 — 반전이 아니므로 같은 요청이 중복 전송돼도 항상 같은 최종 상태로
     * 수렴한다(멱등). {@code desiredChecked}가 null이면(본문 없는 레거시 호출) 하위호환을 위해
     * 기존 반전(toggle) 방식으로 처리한다.
     *
     * [해결됨 — 2026-08-08, append-only 히스토리 버그] 대표 RiskScore 갱신은
     * {@link com.consentradar.consentradar.riskhistory.PersonalRiskHistoryService#saveOrUpdateToday}에
     * 위임한다. 과거에는 이 메서드가 직접 "가장 최근 대표 row"를 날짜 무관하게 찾아 덮어쓰고
     * 그 row의 scoredAt까지 오늘로 바꿔버렸다 — 즉 사용자가 여러 날에 걸쳐 토글해도 실제로는
     * row가 1개만 남아 위험도 추이 히스토리(2-4 API)가 쌓이지 않는 버그였다(docs/known_issues.md
     * 참고). 지금은 "오늘자 row"로 조회 범위를 좁혀 upsert하므로 과거 날짜 row는 보존된다.
     */
    @Transactional
    public ConsentPatchResponse toggleConsent(Long userId, Long consentItemId, Boolean desiredChecked) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        ConsentItem consentItem = consentItemRepository.findById(consentItemId)
                .orElseThrow(() -> new IllegalArgumentException("동의항목을 찾을 수 없습니다: " + consentItemId));

        // 기존 체크 레코드가 있으면 갱신, 없으면 신규 생성
        UserConsentCheck check = userConsentCheckRepository
                .findByUser_UserIdAndConsentItem_ConsentItemId(userId, consentItemId)
                .orElseGet(() -> {
                    UserConsentCheck c = new UserConsentCheck();
                    c.setUser(user);
                    c.setConsentItem(consentItem);
                    return c;
                });

        check.setChecked(desiredChecked != null ? desiredChecked : !check.isChecked());
        userConsentCheckRepository.save(check);
        userConsentHistoryRecorder.record(user, consentItem, check.isChecked());

        Company company = consentItem.getCompany();
        RiskResult newResult = safeCalculate(userId, company.getCompanyId());

        // 사용자별 개인 맞춤 대표 RiskScore 갱신 (company+user 로 구분되는 row. 배치용 user=null row와 별개)
        // 오늘자 row는 upsert(있으면 갱신)하고 과거 날짜 row는 건드리지 않는다(append-only).
        if (newResult != null) {
            personalRiskHistoryService.saveOrUpdateToday(user, company, newResult);
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
                .map(company -> new CompanyRiskResponse(company, safeCalculate(userId, company.getCompanyId())))
                .sorted(Comparator.comparing(
                        r -> r.getRiskScore() != null ? r.getRiskScore() : BigDecimal.ZERO,
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    /**
     * [수정 이력 — 잘못된 데이터 하나가 전체 API를 죽이는 문제 방어]
     * PersonalRiskCalculator.calculate()는 ConsentItem의 DS/ES/TF 점수가 유효값(1,3,5 /
     * 1,2,3)을 벗어나면 IllegalArgumentException을 던지도록 바뀌었다(데이터 오염을 조용히
     * 삼키지 않기 위함). 그런데 getCompaniesSortedByRisk()는 이 계산을 기업 목록 전체에 대해
     * 스트림으로 도는데, 그대로 두면 기업 하나의 데이터만 잘못돼도 예외가 스트림 전체를
     * 끊어서 목록 조회 API 전체가 500으로 죽는다 — 잘못된 데이터를 "드러내는" 목적이었지
     * 관련 없는 다른 기업까지 전부 못 보게 만들려던 게 아니다. 그 기업만 위험도 미상(null)으로
     * 처리하고 나머지는 정상 응답하도록 이 메서드를 통해 예외를 여기서 흡수한다.
     */
    private RiskResult safeCalculate(Long userId, Long companyId) {
        try {
            return personalRiskCalculator.calculate(userId, companyId);
        } catch (IllegalArgumentException e) {
            log.warn("companyId={} 위험도 계산 실패 — 잘못된 동의 항목 데이터로 추정, 위험도 미상 처리: {}",
                    companyId, e.getMessage());
            return null;
        }
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
