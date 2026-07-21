package com.dynamicconsent.llm.parser;

import com.dynamicconsent.llm.dto.LlmRiskAnalysisResponse;
import com.dynamicconsent.llm.exception.LlmParseException;
import com.dynamicconsent.model.RiskInput;
import com.dynamicconsent.model.variable.DataSensitivity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLM 응답 파싱(1-4) 회귀 테스트
 *
 * 대상: {@link LlmResponseParser#parse(String)}
 * 처리 순서: 마크다운/텍스트에서 JSON 추출 → 역직렬화 → Enum 정규화 → 필수 필드·유효값 검증
 */
class LlmResponseParserTest {

    private static final String VALID_JSON = """
            {
              "companyName": "카카오",
              "consentItems": [
                {
                  "itemName": "위치정보 수집",
                  "ds": "HIGH",
                  "es": "HIGH",
                  "tf": "LONG",
                  "pc": "NON_COMPLIANT",
                  "ai": "HIGH_RISK"
                }
              ]
            }
            """;

    // ── 정상 파싱 ─────────────────────────────────────────────────

    @Test
    void parsesValidJson() {
        LlmRiskAnalysisResponse result = LlmResponseParser.parse(VALID_JSON);
        assertEquals("카카오", result.getCompanyName());
        assertEquals(1, result.getConsentItems().size());
        assertEquals("위치정보 수집", result.getConsentItems().get(0).getItemName());
        assertEquals("HIGH", result.getConsentItems().get(0).getDs());
    }

    @Test
    void stripsMarkdownCodeBlock() {
        String withMarkdown = "```json\n" + VALID_JSON + "\n```";
        LlmRiskAnalysisResponse result = LlmResponseParser.parse(withMarkdown);
        assertEquals("카카오", result.getCompanyName());
    }

    @Test
    void extractsJsonFromSurroundingText() {
        // LLM이 JSON 앞뒤에 설명 문장을 덧붙이는 경우 최외곽 중괄호만 추출
        String withText = "분석 결과는 다음과 같습니다:\n" + VALID_JSON + "\n이상입니다.";
        LlmRiskAnalysisResponse result = LlmResponseParser.parse(withText);
        assertEquals("카카오", result.getCompanyName());
    }

    @Test
    void normalizesLowerCaseEnumValues() {
        String lowerCaseJson = """
                {
                  "companyName": "네이버",
                  "consentItems": [
                    {
                      "itemName": "이메일 수집",
                      "ds": "moderate",
                      "es": "medium",
                      "tf": "short",
                      "pc": "compliant",
                      "ai": "low_risk"
                    }
                  ]
                }
                """;
        LlmRiskAnalysisResponse result = LlmResponseParser.parse(lowerCaseJson);
        assertEquals("MODERATE", result.getConsentItems().get(0).getDs());
        assertEquals("LOW_RISK", result.getConsentItems().get(0).getAi());
    }

    @Test
    void parsedResultConvertsToRiskInput() {
        // 파싱 결과가 RiskCalculator 입력으로 정상 변환되는지 (정규화 후 valueOf 성공)
        LlmRiskAnalysisResponse result = LlmResponseParser.parse(VALID_JSON);
        RiskInput input = result.getConsentItems().get(0).toRiskInput();
        assertEquals(DataSensitivity.HIGH, input.getDataSensitivity());
    }

    // ── 필수 필드 누락 ────────────────────────────────────────────

    @Test
    void throwsWhenCompanyNameMissing() {
        String json = """
                {
                  "consentItems": [
                    { "itemName": "이름", "ds": "HIGH", "es": "HIGH", "tf": "LONG",
                      "pc": "NON_COMPLIANT", "ai": "HIGH_RISK" }
                  ]
                }
                """;
        LlmParseException ex = assertThrows(LlmParseException.class,
                () -> LlmResponseParser.parse(json));
        assertTrue(ex.getMessage().contains("companyName"));
    }

    @Test
    void throwsWhenConsentItemsEmpty() {
        String json = """
                {
                  "companyName": "카카오",
                  "consentItems": []
                }
                """;
        LlmParseException ex = assertThrows(LlmParseException.class,
                () -> LlmResponseParser.parse(json));
        assertTrue(ex.getMessage().contains("consentItems"));
    }

    @Test
    void throwsWhenItemNameMissing() {
        String json = """
                {
                  "companyName": "카카오",
                  "consentItems": [
                    { "ds": "HIGH", "es": "HIGH", "tf": "LONG",
                      "pc": "NON_COMPLIANT", "ai": "HIGH_RISK" }
                  ]
                }
                """;
        LlmParseException ex = assertThrows(LlmParseException.class,
                () -> LlmResponseParser.parse(json));
        assertTrue(ex.getMessage().contains("itemName"));
    }

    @Test
    void throwsWhenEnumFieldMissing() {
        // ds 필드 자체가 없는 경우
        String json = """
                {
                  "companyName": "카카오",
                  "consentItems": [
                    { "itemName": "광고수신", "es": "HIGH", "tf": "LONG",
                      "pc": "NON_COMPLIANT", "ai": "HIGH_RISK" }
                  ]
                }
                """;
        LlmParseException ex = assertThrows(LlmParseException.class,
                () -> LlmResponseParser.parse(json));
        assertTrue(ex.getMessage().contains("ds"));
    }

    // ── 유효하지 않은 값 ──────────────────────────────────────────

    @Test
    void throwsWhenEnumValueInvalid() {
        String json = """
                {
                  "companyName": "카카오",
                  "consentItems": [
                    { "itemName": "광고수신", "ds": "INVALID", "es": "HIGH", "tf": "LONG",
                      "pc": "NON_COMPLIANT", "ai": "HIGH_RISK" }
                  ]
                }
                """;
        assertThrows(LlmParseException.class, () -> LlmResponseParser.parse(json));
    }

    // ── 입력 자체가 비정상 ────────────────────────────────────────

    @Test
    void throwsWhenInputNull() {
        assertThrows(LlmParseException.class, () -> LlmResponseParser.parse(null));
    }

    @Test
    void throwsWhenInputBlank() {
        assertThrows(LlmParseException.class, () -> LlmResponseParser.parse("   "));
    }

    @Test
    void throwsWhenNoJsonObjectPresent() {
        assertThrows(LlmParseException.class,
                () -> LlmResponseParser.parse("JSON이 전혀 없는 일반 텍스트"));
    }
}
