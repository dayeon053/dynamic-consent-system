package com.consentradar.consentradar.common;

/**
 * 프로젝트 공통 에러 응답 형식 — {@code {"message": "..."}}.
 *
 * admin API(400/404/409)가 쓰던 {@code AdminErrorResponse}와 동일한 형태였던 것을
 * 이 공용 위치로 옮겨, admin이 아닌 API(예: PATCH /users/{userId}/consents/{consentItemId}의
 * 존재하지 않는 userId 404 응답)에서도 같은 형식을 재사용한다.
 */
public class ErrorResponse {

    private final String message;

    public ErrorResponse(String message) {
        this.message = message;
    }

    public String getMessage() { return message; }
}
