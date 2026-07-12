package com.dynamicconsent.llm.parser;

import com.dynamicconsent.llm.dto.ConsentItemAnalysis;
import com.dynamicconsent.llm.dto.LlmRiskAnalysisResponse;
import com.dynamicconsent.llm.exception.LlmParseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 원시 출력 문자열을 파싱하고 유효성을 검증하는 파서
 *
 * 처리 순서:
 *   1) 마크다운 코드블록 제거 (LLM이 ```json ... ``` 형태로 감싸는 경우 대응)
 *   2) JSON 최외곽 중괄호 추출 (앞뒤 설명 텍스트 무시)
 *   3) Jackson으로 역직렬화
 *   4) 필수 필드 존재 검증
 *   5) Enum 값 대소문자 정규화 후 유효값 검증
 */
public class LlmResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 마크다운 코드블록 패턴: ```json ... ``` 또는 ``` ... ```
    private static final Pattern MARKDOWN_PATTERN =
            Pattern.compile("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```", Pattern.DOTALL);

    // JSON 최외곽 중괄호 추출 패턴 (코드블록 없이 바로 JSON만 있거나, 앞뒤 설명이 있을 때)
    private static final Pattern JSON_OBJECT_PATTERN =
            Pattern.compile("(\\{[\\s\\S]*\\})", Pattern.DOTALL);

    private static final Set<String> VALID_DS = Set.of("LOW", "MODERATE", "HIGH");
    private static final Set<String> VALID_ES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> VALID_TF = Set.of("SHORT", "MEDIUM", "LONG");
    private static final Set<String> VALID_PC = Set.of("COMPLIANT", "NON_COMPLIANT");
    private static final Set<String> VALID_AI = Set.of("LOW_RISK", "HIGH_RISK");

    private LlmResponseParser() {}

    /**
     * LLM 원시 출력 문자열을 파싱한다.
     *
     * @param rawLlmOutput LLM이 반환한 원시 문자열
     * @return 파싱 및 검증이 완료된 LlmRiskAnalysisResponse
     * @throws LlmParseException JSON 파싱 실패 또는 유효성 검증 실패 시
     */
    public static LlmRiskAnalysisResponse parse(String rawLlmOutput) {
        if (rawLlmOutput == null || rawLlmOutput.isBlank()) {
            throw new LlmParseException("LLM 응답이 비어있습니다.");
        }

        String jsonString = extractJson(rawLlmOutput);

        LlmRiskAnalysisResponse response;
        try {
            response = MAPPER.readValue(jsonString, LlmRiskAnalysisResponse.class);
        } catch (Exception e) {
            throw new LlmParseException("JSON 역직렬화 실패: " + e.getMessage(), e);
        }

        normalizeEnumValues(response);
        validate(response);
        return response;
    }

    /** 마크다운 코드블록 또는 앞뒤 텍스트에서 JSON 객체를 추출한다 */
    private static String extractJson(String raw) {
        // 1) 마크다운 코드블록 우선 시도
        Matcher markdownMatcher = MARKDOWN_PATTERN.matcher(raw);
        if (markdownMatcher.find()) {
            return markdownMatcher.group(1).trim();
        }

        // 2) 최외곽 중괄호 추출
        Matcher jsonMatcher = JSON_OBJECT_PATTERN.matcher(raw);
        if (jsonMatcher.find()) {
            return jsonMatcher.group(1).trim();
        }

        throw new LlmParseException("응답에서 JSON 객체를 찾을 수 없습니다. 원본:\n" + raw);
    }

    /**
     * LLM이 소문자/혼합 케이스로 반환하는 경우를 대비해 Enum 필드를 대문자로 정규화한다.
     * 예: "low" → "LOW", "non_compliant" → "NON_COMPLIANT"
     */
    private static void normalizeEnumValues(LlmRiskAnalysisResponse response) {
        if (response.getConsentItems() == null) return;
        for (ConsentItemAnalysis item : response.getConsentItems()) {
            if (item.getDs() != null) item.setDs(item.getDs().toUpperCase().trim());
            if (item.getEs() != null) item.setEs(item.getEs().toUpperCase().trim());
            if (item.getTf() != null) item.setTf(item.getTf().toUpperCase().trim());
            if (item.getPc() != null) item.setPc(item.getPc().toUpperCase().trim());
            if (item.getAi() != null) item.setAi(item.getAi().toUpperCase().trim());
        }
    }

    /** 필수 필드 존재 및 Enum 유효값 검증 */
    private static void validate(LlmRiskAnalysisResponse response) {
        if (response.getCompanyName() == null || response.getCompanyName().isBlank()) {
            throw new LlmParseException("필수 필드 누락: companyName");
        }

        List<ConsentItemAnalysis> items = response.getConsentItems();
        if (items == null || items.isEmpty()) {
            throw new LlmParseException("필수 필드 누락: consentItems (빈 배열)");
        }

        for (int i = 0; i < items.size(); i++) {
            validateItem(items.get(i), i);
        }
    }

    private static void validateItem(ConsentItemAnalysis item, int index) {
        String prefix = "consentItems[" + index + "]";

        if (item.getItemName() == null || item.getItemName().isBlank()) {
            throw new LlmParseException(prefix + ".itemName 누락");
        }

        validateEnum(prefix + ".ds", item.getDs(), VALID_DS);
        validateEnum(prefix + ".es", item.getEs(), VALID_ES);
        validateEnum(prefix + ".tf", item.getTf(), VALID_TF);
        validateEnum(prefix + ".pc", item.getPc(), VALID_PC);
        validateEnum(prefix + ".ai", item.getAi(), VALID_AI);
    }

    private static void validateEnum(String fieldPath, String value, Set<String> validValues) {
        if (value == null || value.isBlank()) {
            throw new LlmParseException(fieldPath + " 누락");
        }
        if (!validValues.contains(value)) {
            throw new LlmParseException(
                    fieldPath + " 유효하지 않은 값: \"" + value + "\" (허용값: " + validValues + ")");
        }
    }
}
