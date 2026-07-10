package com.dynamicconsent.llm.retry;

import com.dynamicconsent.llm.dto.LlmRiskAnalysisResponse;
import com.dynamicconsent.llm.exception.LlmParseException;
import com.dynamicconsent.llm.exception.LlmRetryExhaustedException;
import com.dynamicconsent.llm.parser.LlmResponseParser;

/**
 * LLM 출력 포맷 고정 예외 처리(Retry) 모듈
 *
 * LLM이 지정된 JSON 포맷을 지키지 않을 경우 오류 원인을 포함한
 * 보정 프롬프트를 자동 생성하여 최대 MAX_RETRIES회 재시도한다.
 *
 * [팀원1 사용 예시]
 *   // LLM API 호출 함수를 람다로 전달
 *   LlmRiskAnalysisResponse result = LlmRetryModule.execute(
 *       initialPrompt,
 *       prompt -> openAiClient.call(prompt)   // 실제 LLM 호출 부분
 *   );
 *   // 이후 result.getConsentItems()를 순회하며 DB 적재
 */
public class LlmRetryModule {

    /** 최대 재시도 횟수 (최초 시도 포함) */
    private static final int MAX_RETRIES = 3;

    private LlmRetryModule() {}

    /**
     * LLM을 호출하는 함수형 인터페이스.
     * 팀원1이 실제 LLM API(OpenAI, Gemini 등) 호출 로직을 이 인터페이스로 구현해 전달한다.
     */
    @FunctionalInterface
    public interface LlmCaller {
        /**
         * 프롬프트를 받아 LLM을 호출하고 응답 문자열을 반환한다.
         *
         * @param prompt LLM에게 보낼 프롬프트
         * @return LLM 원시 응답 문자열
         */
        String call(String prompt);
    }

    /**
     * LLM을 호출하고, 응답 파싱 실패 시 보정 프롬프트로 재시도한다.
     *
     * @param initialPrompt 최초 LLM에게 보낼 프롬프트
     * @param caller        실제 LLM API 호출 로직 (팀원1이 구현)
     * @return 파싱 성공한 LlmRiskAnalysisResponse
     * @throws LlmRetryExhaustedException MAX_RETRIES 초과 시
     */
    public static LlmRiskAnalysisResponse execute(String initialPrompt, LlmCaller caller) {
        String currentPrompt = initialPrompt;
        LlmParseException lastException = null;
        String lastRawOutput = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            lastRawOutput = caller.call(currentPrompt);

            try {
                LlmRiskAnalysisResponse response = LlmResponseParser.parse(lastRawOutput);
                if (attempt > 1) {
                    System.out.printf("[LlmRetryModule] %d번째 시도에서 파싱 성공%n", attempt);
                }
                return response;

            } catch (LlmParseException e) {
                lastException = e;
                System.out.printf("[LlmRetryModule] %d/%d 시도 실패: %s%n",
                        attempt, MAX_RETRIES, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    currentPrompt = buildCorrectionPrompt(lastRawOutput, e.getMessage());
                }
            }
        }

        throw new LlmRetryExhaustedException(
                String.format("LLM 응답 파싱 %d회 모두 실패. 마지막 오류: %s",
                        MAX_RETRIES, lastException != null ? lastException.getMessage() : "unknown"),
                MAX_RETRIES,
                lastException
        );
    }

    /**
     * 파싱 실패 원인과 이전 응답을 포함한 보정 프롬프트를 생성한다.
     * LLM에게 무엇이 잘못됐는지 명확히 알려줘 올바른 형식으로 재출력하게 유도한다.
     */
    private static String buildCorrectionPrompt(String failedOutput, String errorMessage) {
        return "이전 응답의 JSON 형식이 올바르지 않아 파싱에 실패했습니다.\n"
                + "오류 원인: " + errorMessage + "\n\n"
                + "이전 응답:\n" + failedOutput + "\n\n"
                + "아래 규칙을 반드시 지켜 JSON만 다시 출력하세요:\n"
                + "1. 마크다운 코드블록(```) 없이 순수 JSON만 출력\n"
                + "2. 루트 키: companyName(string), consentItems(array)\n"
                + "3. 각 항목 필수 키: itemName, itemType, ds, es, tf, pc, ai\n"
                + "4. ds 허용값: LOW | MODERATE | HIGH\n"
                + "5. es 허용값: LOW | MEDIUM | HIGH\n"
                + "6. tf 허용값: SHORT | MEDIUM | LONG\n"
                + "7. pc 허용값: COMPLIANT | NON_COMPLIANT\n"
                + "8. ai 허용값: LOW_RISK | HIGH_RISK\n"
                + "9. 모든 값은 대문자로 출력\n\n"
                + "올바른 출력 예시:\n"
                + "{\n"
                + "  \"companyName\": \"기업명\",\n"
                + "  \"consentItems\": [\n"
                + "    {\n"
                + "      \"itemName\": \"마케팅 수신 동의\",\n"
                + "      \"itemType\": \"OPTIONAL\",\n"
                + "      \"ds\": \"MODERATE\",\n"
                + "      \"es\": \"HIGH\",\n"
                + "      \"tf\": \"LONG\",\n"
                + "      \"pc\": \"NON_COMPLIANT\",\n"
                + "      \"ai\": \"HIGH_RISK\",\n"
                + "      \"dsReason\": \"판단 근거\",\n"
                + "      \"esReason\": \"판단 근거\",\n"
                + "      \"tfReason\": \"판단 근거\",\n"
                + "      \"pcReason\": \"판단 근거\",\n"
                + "      \"aiReason\": \"판단 근거\"\n"
                + "    }\n"
                + "  ]\n"
                + "}";
    }
}
