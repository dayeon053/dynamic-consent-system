package com.consentradar.consentradar.admin;

import com.consentradar.consentradar.scheduler.CompanyCrawlResult;
import com.consentradar.consentradar.scheduler.PolicyCrawlScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
     * POST /admin/crawl/{companyId}
     * 특정 기업 1건에 대해 크롤링 → 변경감지 → (최초 수집이거나 변경 있으면) 위험도 재산출을
     * 즉시 동기적으로 실행한다.
     */
    @PostMapping("/admin/crawl/{companyId}")
    public ResponseEntity<AdminCrawlTriggerResponse> triggerCrawl(@PathVariable Long companyId) {
        try {
            CompanyCrawlResult result = policyCrawlScheduler.runForCompany(companyId);
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
