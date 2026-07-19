package com.dynamicconsent.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * LLM이 반환하는 기업 전체 분석 결과 최상위 DTO
 *
 * LLM 출력 JSON의 루트 객체에 대응한다.
 *
 * 예시 JSON:
 * {
 *   "companyName": "카카오",
 *   "consentItems": [
 *     {
 *       "itemName": "마케팅 수신 동의",
 *       "itemType": "OPTIONAL",
 *       "ds": "MODERATE",
 *       "es": "HIGH",
 *       "tf": "LONG",
 *       "pc": "NON_COMPLIANT",
 *       "ai": "HIGH_RISK",
 *       "dsReason": "복합 식별 정보(행태정보, 위치정보) 포함",
 *       "esReason": "광고주, 브랜드 파트너 등 불특정 다수 외부 공유",
 *       "tfReason": "12개월 이상 장기 보관",
 *       "pcReason": "마케팅 및 기타 목적으로 포괄적 동의",
 *       "aiReason": "맞춤형 광고를 위한 자동화 프로파일링"
 *     }
 *   ]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmRiskAnalysisResponse {

    @JsonProperty("companyName")
    private String companyName;

    @JsonProperty("consentItems")
    private List<ConsentItemAnalysis> consentItems;

    public String getCompanyName()                    { return companyName; }
    public List<ConsentItemAnalysis> getConsentItems() { return consentItems; }
}
