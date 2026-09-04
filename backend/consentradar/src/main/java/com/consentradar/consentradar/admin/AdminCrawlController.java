package com.consentradar.consentradar.admin;

import com.consentradar.consentradar.scheduler.CompanyCrawlResult;
import com.consentradar.consentradar.scheduler.PolicyCrawlScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 수동 크롤링 트리거 API.
 *
 * PoC 단계라 인증/권한 체계가 없다 — 현재는 누구나 호출 가능하다.
 * 인증은 Sprint 04에서 추가 예정이며, 그 전까지는 개발/데모 목적으로만 사용한다.
 */
@RestController
public class AdminCrawlController {

    private final PolicyCrawlScheduler policyCrawlScheduler;

    public AdminCrawlController(PolicyCrawlScheduler policyCrawlScheduler) {
        this.policyCrawlScheduler = policyCrawlScheduler;
    }

    /**
     * POST /admin/crawl/{companyId}?force=true
     * 특정 기업 1건에 대해 크롤링 → 변경감지 → (최초 수집이거나 변경 있으면) 위험도 재산출을
     * 즉시 동기적으로 실행한다.
     *
     * force=true면 변경 여부(shouldAnalyze 판단)와 무관하게 위험도 재산출을 강제로 실행한다.
     * 재크롤링 텍스트가 동일해도 예전 오염된 ConsentItem을 정리해야 하는 관리 목적으로 쓴다
     * — 예를 들어 페이지 내용은 안 바뀌었는데 그 회사의 ConsentItem에 예전(mock 등) 항목이
     * 그대로 active로 남아있어 위험도 계산에 잘못 섞여 들어가는 경우, force=true로 재분석을
     * 강제해야 {@code ConsentItemUpsertService.deactivateMissing()}이 그 예전 항목을 소프트
     * 삭제하도록 만들 수 있다. `/admin/**`이므로 기존 ROLE_ADMIN 인증이 그대로 적용된다.
     */
    @PostMapping("/admin/crawl/{companyId}")
    public ResponseEntity<AdminCrawlTriggerResponse> triggerCrawl(
            @PathVariable Long companyId,
            @RequestParam(name = "force", required = false, defaultValue = "false") boolean force) {
        try {
            CompanyCrawlResult result = policyCrawlScheduler.runForCompany(companyId, force);
            return ResponseEntity.ok(AdminCrawlTriggerResponse.success(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AdminCrawlTriggerResponse.failure(companyId, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AdminCrawlTriggerResponse.failure(companyId, e.getMessage()));
        }
    }
}
