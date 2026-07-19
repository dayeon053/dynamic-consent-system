package com.consentradar.consentradar.api;

import com.consentradar.consentradar.api.dto.CompanyRiskResponse;
import com.consentradar.consentradar.api.dto.ConsentPatchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
public class ConsentApiController {

    private final ConsentApiService consentApiService;

    public ConsentApiController(ConsentApiService consentApiService) {
        this.consentApiService = consentApiService;
    }

    /**
     * PATCH /users/{userId}/consents/{consentItemId}
     * 사용자의 동의 항목 체크 상태를 토글하고, 해당 기업의 재산출된 위험도를 반환한다.
     */
    @PatchMapping("/users/{userId}/consents/{consentItemId}")
    public ResponseEntity<ConsentPatchResponse> toggleConsent(
            @PathVariable Long userId,
            @PathVariable Long consentItemId) {
        ConsentPatchResponse response = consentApiService.toggleConsent(userId, consentItemId);
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
}
