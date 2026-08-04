package com.consentradar.consentradar.api;

import com.consentradar.consentradar.api.dto.CompanyRiskResponse;
import com.consentradar.consentradar.api.dto.ConsentItemResponse;
import com.consentradar.consentradar.api.dto.ConsentPatchRequest;
import com.consentradar.consentradar.api.dto.ConsentPatchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
public class ConsentApiController {

    private final ConsentApiService consentApiService;
    private final ConcurrentUpdateRetrier retrier;

    public ConsentApiController(ConsentApiService consentApiService, ConcurrentUpdateRetrier retrier) {
        this.consentApiService = consentApiService;
        this.retrier = retrier;
    }

    /**
     * PATCH /users/{userId}/consents/{consentItemId}
     * 사용자의 동의 항목 체크 상태를 요청 본문의 {@code checked} 값으로 설정하고, 해당
     * 기업의 재산출된 위험도를 반환한다.
     *
     * [멱등성 개선 — A안] 기존에는 본문 없이 호출해 서버가 현재 상태를 반전시켰다. 클라이언트가
     * 원하는 최종 상태를 모르는 채로 반전에만 의존하면, 네트워크 재시도(OkHttp
     * retryOnConnectionFailure=true 등)로 같은 요청이 중복 전송될 때 짝수 번 반전되어 사용자
     * 의도와 반대 상태로 저장될 수 있다. {@code checked}를 명시하면 몇 번 재시도되어도 항상
     * 같은 최종 상태로 수렴한다(멱등). 본문이 없거나 {@code checked}가 없는 레거시 호출은
     * 하위호환을 위해 기존 반전 방식으로 그대로 처리한다.
     *
     * 같은 기업의 선택동의 여러 개를 짧은 시간 안에 토글하면 대표 RiskScore row를 두고
     * 동시 요청끼리 경합할 수 있다(자세한 내용은 {@link ConsentApiService#toggleConsent}
     * 문서 참고). 경합 실패는 {@link ConcurrentUpdateRetrier}가 감지해 트랜잭션 전체를
     * 처음부터 재시도한다 — 컨트롤러(트랜잭션 경계 바깥)에서 서비스 메서드를 프록시로
     * 다시 호출해야 매 시도가 새 트랜잭션이 되므로, 재시도는 여기서 수행한다.
     */
    @PatchMapping("/users/{userId}/consents/{consentItemId}")
    public ResponseEntity<ConsentPatchResponse> toggleConsent(
            @PathVariable Long userId,
            @PathVariable Long consentItemId,
            @RequestBody(required = false) ConsentPatchRequest request) {
        Boolean desiredChecked = request != null ? request.getChecked() : null;
        ConsentPatchResponse response =
                retrier.retry(() -> consentApiService.toggleConsent(userId, consentItemId, desiredChecked));
        return ResponseEntity.ok(response);
    }

    /**
     * GET /companies?userId={userId}&sort=risk_score_desc
     * 기업 목록을 대표 위험도 내림차순으로 반환한다. userId는 향후 필터링 확장용.
     */
    @GetMapping("/companies")
    public ResponseEntity<List<CompanyRiskResponse>> getCompanies(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "risk_score_desc") String sort) {
        List<CompanyRiskResponse> response = consentApiService.getCompaniesSortedByRisk(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /companies/{companyId}/consent-items?userId={userId}
     * 기업의 필수/선택 동의 항목 전체를 5대 변수 값과 함께 반환한다. 동의 세부사항 탭(4-5)에서
     * 사용하며, 각 항목의 consentItemId를 PATCH /users/{userId}/consents/{consentItemId}에 사용한다.
     */
    @GetMapping("/companies/{companyId}/consent-items")
    public ResponseEntity<List<ConsentItemResponse>> getConsentItems(
            @PathVariable Long companyId,
            @RequestParam Long userId) {
        List<ConsentItemResponse> response = consentApiService.getConsentItems(userId, companyId);
        return ResponseEntity.ok(response);
    }
}
