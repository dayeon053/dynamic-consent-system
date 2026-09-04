package com.consentradar.consentradar.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 컨트롤러에서 직접 처리하지 않고 그대로 흘러나온 {@link IllegalArgumentException}을
 * HTTP 404로 변환한다.
 *
 * 적용 대상(2026-08-25 기준 코드 전수 확인): {@link com.consentradar.consentradar.api.ConsentApiService#toggleConsent}
 * 가 던지는 "존재하지 않는 userId" / "존재하지 않는 consentItemId" 두 케이스뿐이다. 그 외
 * {@code IllegalArgumentException}을 던지는 지점(AdminController, AdminCrawlController,
 * AdminCompanyService, PolicyCrawlProcessor, ConsentApiService.safeCalculate)은 전부
 * 컨트롤러/서비스 내부에서 이미 로컬로 잡아 처리하므로 이 핸들러까지 도달하지 않는다 —
 * 즉 이 핸들러를 추가해도 기존 400/409 응답 로직에는 영향이 없다(api_spec_v2_final.md
 * 결정 사항 5번).
 *
 * PATCH /users/{userId}/consents/{consentItemId}가 존재하지 않는 userId로 호출되면
 * 과거에는 처리 핸들러가 없어 Spring 기본 처리로 HTTP 500이 나갔다(2026-07-30 실측 확인,
 * docs/api_spec_v2_final.md 2-3절). 이 핸들러로 그 갭을 해소한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }
}
