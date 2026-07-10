package com.dynamicconsent.llm.exception;

/** 최대 재시도 횟수를 초과해도 LLM 응답 파싱에 실패했을 때 발생 */
public class LlmRetryExhaustedException extends RuntimeException {

    private final int attemptCount;

    public LlmRetryExhaustedException(String message, int attemptCount, Throwable cause) {
        super(message, cause);
        this.attemptCount = attemptCount;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}
