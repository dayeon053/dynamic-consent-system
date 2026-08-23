package com.consentradar.consentradar;

import com.dynamicconsent.llm.dto.LlmRiskAnalysisResponse;
import com.dynamicconsent.llm.exception.LlmParseException;
import com.dynamicconsent.llm.parser.LlmResponseParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmResponseParserTest {

    private static final String VALID_JSON = """
            {
              "companyName": "카카오",
              "consentItems": [
                {
                  "itemName": "위치정보 수집",
                  "itemType": "REQUIRED",
                  "ds": "HIGH",
                  "es": "HIGH",
                  "tf": "LONG",
                  "pc": "NON_COMPLIANT",
                  "ai": "HIGH_RISK"
                }
              ]
            }
            """;

    @Test
    void 정상_JSON_파싱_성공() {
        LlmRiskAnalysisResponse result = LlmResponseParser.parse(VALID_JSON);
        assertEquals("카카오", result.getCompanyName());
        assertEquals(1, result.getConsentItems().size());
        assertEquals("위치정보 수집", result.getConsentItems().get(0).getItemName());
    }

    @Test
    void 마크다운_코드블록_제거_후_파싱_성공() {
        String withMarkdown = "```json\n" + VALID_JSON + "\n```";
        LlmRiskAnalysisResponse result = LlmResponseParser.parse(withMarkdown);
        assertEquals("카카오", result.getCompanyName());
    }

    @Test
    void 소문자_enum_값_정규화_성공() {
        String lowerCaseJson = """
                {
                  "companyName": "네이버",
                  "consentItems": [
                    {
                      "itemName": "이메일 수집",
                      "itemType": "optional",
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
        assertEquals("OPTIONAL", result.getConsentItems().get(0).getItemType());
        assertEquals("MODERATE", result.getConsentItems().get(0).getDs());
    }

    @Test
    void companyName_누락시_예외_발생() {
        String json = """
                {
                  "consentItems": [
                    {
                      "itemName": "이름",
                      "itemType": "REQUIRED",
                      "ds": "HIGH", "es": "HIGH", "tf": "LONG",
                      "pc": "NON_COMPLIANT", "ai": "HIGH_RISK"
                    }
                  ]
                }
                """;
        LlmParseException ex = assertThrows(LlmParseException.class, () -> LlmResponseParser.parse(json));
        assertTrue(ex.getMessage().contains("companyName"));
    }

    @Test
    void consentItems_빈배열시_예외_발생() {
        String json = """
                {
                  "companyName": "카카오",
                  "consentItems": []
                }
                """;
        LlmParseException ex = assertThrows(LlmParseException.class, () -> LlmResponseParser.parse(json));
        assertTrue(ex.getMessage().contains("consentItems"));
    }

    @Test
    void itemType_누락시_예외_발생() {
        String json = """
                {
                  "companyName": "카카오",
                  "consentItems": [
                    {
                      "itemName": "광고수신",
                      "ds": "HIGH", "es": "HIGH", "tf": "LONG",
                      "pc": "NON_COMPLIANT", "ai": "HIGH_RISK"
                    }
                  ]
                }
                """;
        LlmParseException ex = assertThrows(LlmParseException.class, () -> LlmResponseParser.parse(json));
        assertTrue(ex.getMessage().contains("itemType"));
    }

    @Test
    void itemType_유효하지_않은_값_예외_발생() {
        // ConsentItem.ItemType.valueOf()가 파서 밖에서 터지기 전에 여기서 먼저 막혀야 한다.
        String json = """
                {
                  "companyName": "카카오",
                  "consentItems": [
                    {
                      "itemName": "광고수신",
                      "itemType": "필수",
                      "ds": "HIGH", "es": "HIGH", "tf": "LONG",
                      "pc": "NON_COMPLIANT", "ai": "HIGH_RISK"
                    }
                  ]
                }
                """;
        LlmParseException ex = assertThrows(LlmParseException.class, () -> LlmResponseParser.parse(json));
        assertTrue(ex.getMessage().contains("itemType"));
    }

    @Test
    void 유효하지_않은_enum값_예외_발생() {
        String json = """
                {
                  "companyName": "카카오",
                  "consentItems": [
                    {
                      "itemName": "광고수신",
                      "itemType": "REQUIRED",
                      "ds": "INVALID",
                      "es": "HIGH", "tf": "LONG",
                      "pc": "NON_COMPLIANT", "ai": "HIGH_RISK"
                    }
                  ]
                }
                """;
        assertThrows(LlmParseException.class, () -> LlmResponseParser.parse(json));
    }

    @Test
    void null_입력시_예외_발생() {
        assertThrows(LlmParseException.class, () -> LlmResponseParser.parse(null));
    }

    @Test
    void 빈문자열_입력시_예외_발생() {
        assertThrows(LlmParseException.class, () -> LlmResponseParser.parse("   "));
    }
}
