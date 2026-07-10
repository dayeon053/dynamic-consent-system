package com.dynamicconsent.llm.prompt;

/**
 * LLM에게 보낼 프롬프트 템플릿
 *
 * 팀원1(크롤러)이 약관 텍스트를 크롤링한 뒤,
 * buildAnalysisPrompt()에 기업명과 약관 텍스트를 넣어 LLM에 전달한다.
 *
 * [팀원1 사용 예시]
 *   String prompt = LlmPromptTemplate.buildAnalysisPrompt("카카오", crawledPolicyText);
 *   LlmRiskAnalysisResponse result = LlmRetryModule.execute(prompt, llmCaller);
 */
public class LlmPromptTemplate {

    private LlmPromptTemplate() {}

    /**
     * 약관 분석 프롬프트를 생성한다.
     *
     * @param companyName  분석 대상 기업명
     * @param policyText   크롤링된 약관 원문
     * @return LLM에게 전달할 완성된 프롬프트 문자열
     */
    public static String buildAnalysisPrompt(String companyName, String policyText) {
        return "당신은 개인정보 처리방침 전문 분석가입니다.\n"
                + "아래 [" + companyName + "] 개인정보처리방침을 읽고, "
                + "동의 항목별로 5대 위험 변수를 분석하여 지정된 JSON 형식으로만 출력하세요.\n\n"

                + "=== 5대 변수 판단 기준 ===\n"
                + "DS (데이터 민감도): 수집 정보가 얼마나 민감한가\n"
                + "  LOW     = 단독 이름/이메일/전화번호처럼 단독으로 피해가 제한적인 정보\n"
                + "  MODERATE = 이름+생년월일, 주소+전화번호, 행태정보처럼 결합 시 신원 노출 가능 정보\n"
                + "  HIGH    = 주민등록번호, 금융계좌, 의료기록, 생체정보 등 직접 피해를 주는 정보\n\n"

                + "ES (노출 범위): 개인정보가 얼마나 많은 외부 주체에 공유되는가\n"
                + "  LOW    = 내부에서만 처리, 또는 담당자만 접근\n"
                + "  MEDIUM = 결제대행사, 마케팅 대행사 등 제한적 제3자 위탁\n"
                + "  HIGH   = 광고주, 해외 서버, 불특정 다수에게 광범위 공유\n\n"

                + "TF (경과 시간/보관 기간): 개인정보를 얼마나 오래 보관하는가\n"
                + "  SHORT  = 6개월 미만 보관\n"
                + "  MEDIUM = 6개월 이상 ~ 12개월 이하 보관\n"
                + "  LONG   = 12개월 초과 장기 보관\n\n"

                + "PC (목적 명확성): 개인정보 처리 목적이 법적으로 명확한가\n"
                + "  COMPLIANT     = 회원 관리, 상품 배송 등 구체적 목적 명시\n"
                + "  NON_COMPLIANT = '마케팅 및 기타', '서비스 개선' 등 포괄적·불명확한 목적\n\n"

                + "AI (AI 위험계수): AI 기술로 개인정보를 자동 분석하는가\n"
                + "  LOW_RISK  = AI 미활용, 또는 단순 챗봇·추천 수준\n"
                + "  HIGH_RISK = 자동화 프로파일링, 맞춤형 광고 AI, 생체인식, 신용평가 AI\n\n"

                + "=== 출력 규칙 ===\n"
                + "- 마크다운 코드블록(```) 없이 순수 JSON만 출력\n"
                + "- 모든 Enum 값은 대문자로 출력\n"
                + "- 동의 항목마다 각 변수 판단 근거를 한 문장으로 작성\n\n"

                + "=== 출력 형식 ===\n"
                + "{\n"
                + "  \"companyName\": \"" + companyName + "\",\n"
                + "  \"consentItems\": [\n"
                + "    {\n"
                + "      \"itemName\": \"동의 항목명\",\n"
                + "      \"itemType\": \"REQUIRED 또는 OPTIONAL\",\n"
                + "      \"ds\": \"LOW | MODERATE | HIGH\",\n"
                + "      \"es\": \"LOW | MEDIUM | HIGH\",\n"
                + "      \"tf\": \"SHORT | MEDIUM | LONG\",\n"
                + "      \"pc\": \"COMPLIANT | NON_COMPLIANT\",\n"
                + "      \"ai\": \"LOW_RISK | HIGH_RISK\",\n"
                + "      \"dsReason\": \"DS 판단 근거\",\n"
                + "      \"esReason\": \"ES 판단 근거\",\n"
                + "      \"tfReason\": \"TF 판단 근거\",\n"
                + "      \"pcReason\": \"PC 판단 근거\",\n"
                + "      \"aiReason\": \"AI 판단 근거\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n\n"

                + "=== 분석 대상 약관 ===\n"
                + policyText;
    }
}
