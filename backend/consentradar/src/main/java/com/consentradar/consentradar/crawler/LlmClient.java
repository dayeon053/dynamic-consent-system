package com.consentradar.consentradar.crawler;

import org.springframework.stereotype.Component;

@Component
public class LlmClient {

    // TODO: API 키 생기면 실제 호출로 교체
    public String call(String policyText) {
        // Mock 응답 - 합의된 JSON 포맷 그대로
        return """
                {
                  "company": {
                    "name": "카카오",
                    "category": "메신저",
                    "policy_url": "https://www.kakaocorp.com/page/detail/9610",
                    "collected_at": "2026-07-04T09:00:00+09:00"
                  },
                  "consent_items": {
                    "required": [
                      {
                        "item_id": "REQ-001",
                        "title": "서비스 이용을 위한 필수 개인정보 수집",
                        "description": "이름, 휴대폰번호, 이메일 등 회원가입 시 필요한 정보 수집",
                        "raw_text_snippet": "회원은 서비스 이용을 위해 다음 정보를 제공해야 합니다..."
                      }
                    ],
                    "optional": [
                      {
                        "item_id": "OPT-001",
                        "title": "마케팅 정보 수신 동의",
                        "description": "이벤트, 프로모션 등 광고성 정보 수신",
                        "raw_text_snippet": "회사는 이벤트 정보를 SMS, 이메일로 발송할 수 있습니다..."
                      }
                    ]
                  },
                  "risk_variables": {
                    "DS": 3,
                    "ES": 3,
                    "TF": 3,
                    "PC": 1.5,
                    "AI": 1.5
                  },
                  "llm_meta": {
                    "model": "claude-sonnet-4-6",
                    "prompt_version": "v1.0",
                    "confidence": 0.85
                  }
                }
                """;
    }
}