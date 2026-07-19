package com.dynamicconsent.llm.exception;

/** LLM 응답 JSON 파싱 또는 유효성 검증 실패 시 발생 */
public class LlmParseException extends RuntimeException {

    public LlmParseException(String message) {
        super(message);
    }

    public LlmParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
