package com.consentradar.consentradar.crawler;

import org.springframework.stereotype.Component;

@Component
public class LlmClient {

    /**
     * 완성된 프롬프트를 받아 LLM을 호출하고 원시 응답 문자열을 반환한다.
     * LlmRetryModule.LlmCaller 인터페이스와 호환되는 시그니처.
     *
     * TODO: API 키 생기면 실제 LLM 호출로 교체
     */
    public String callWithPrompt(String prompt) {
        // Mock 응답 — LlmResponseParser가 기대하는 포맷
        return """
                {
                  "companyName": "카카오",
                  "consentItems": [
                    {
                      "itemName": "서비스 이용을 위한 필수 개인정보 수집",
                      "itemType": "REQUIRED",
                      "ds": "HIGH",
                      "es": "MEDIUM",
                      "tf": "LONG",
                      "pc": "COMPLIANT",
                      "ai": "LOW_RISK",
                      "dsReason": "이름, 휴대폰번호, 이메일 등 민감한 식별 정보를 수집함",
                      "esReason": "서비스 내부 및 계열사 일부 공유",
                      "tfReason": "회원 탈퇴 후에도 법적 의무 보관 기간(5년) 적용",
                      "pcReason": "수집 목적이 약관에 명확히 기재되어 있음",
                      "aiReason": "자동화 의사결정에 활용되지 않음"
                    },
                    {
                      "itemName": "마케팅 정보 수신 동의",
                      "itemType": "OPTIONAL",
                      "ds": "MODERATE",
                      "es": "HIGH",
                      "tf": "LONG",
                      "pc": "NON_COMPLIANT",
                      "ai": "HIGH_RISK",
                      "dsReason": "구매 이력, 관심사 등 행동 데이터 포함",
                      "esReason": "제3자 광고 파트너사에 폭넓게 공유됨",
                      "tfReason": "동의 철회 전까지 무기한 보관",
                      "pcReason": "마케팅 활용 범위가 포괄적으로만 기재되어 GDPR Article 5 위반 소지",
                      "aiReason": "개인화 광고 타겟팅 알고리즘에 활용됨"
                    }
                  ]
                }
                """;
    }
}
