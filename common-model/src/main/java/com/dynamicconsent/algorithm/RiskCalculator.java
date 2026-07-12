package com.dynamicconsent.algorithm;

import com.dynamicconsent.model.RiskGrade;
import com.dynamicconsent.model.RiskInput;
import com.dynamicconsent.model.RiskResult;

import java.util.Objects;

/**
 * 위험도 산출 계산기 — 2단계 완전 구현
 *
 * 산정식: Risk Score = DS + (ES × TF × PC × AI) × 2
 * 점수 범위: 3.0 ~ 45.5
 *
 * [팀원1 BE 적재 방법]
 *   RiskInput input = new RiskInput(DS, ES, TF, PC, AI);
 *   RiskResult result = RiskCalculator.calculate(input);
 *   entity.setRiskScore(result.getScore());
 *   entity.setRiskGrade(result.getGrade().name());
 *
 * [팀원2 프론트 연동 방법]
 *   사용자가 동의 스위치 변경 시 변경된 변수로 RiskInput 재조합 후
 *   RiskCalculator.calculate(input) 재호출 → UI 점수 갱신
 */
public class RiskCalculator {

    private RiskCalculator() {}

    /**
     * 5대 변수 입력값으로 위험도 점수와 등급을 산출한다.
     *
     * @param input DS/ES/TF/PC/AI 를 담은 RiskInput
     * @return 점수(double, 소수점 1자리)와 등급(RiskGrade)을 담은 RiskResult
     * @throws NullPointerException input 또는 내부 변수가 null인 경우
     */
    public static RiskResult calculate(RiskInput input) {
        validateInput(input);

        double ds = input.getDataSensitivity().score;
        double es = input.getExposureScope().score;
        double tf = input.getTimeFactor().score;
        double pc = input.getPurposeClarity().score;
        double ai = input.getAiRiskFactor().score;

        // Risk Score = DS + (ES × TF × PC × AI) × 2
        double compoundFactor = es * tf * pc * ai;
        double rawScore = ds + compoundFactor * 2;

        // 소수점 첫째 자리 반올림
        double score = Math.round(rawScore * 10.0) / 10.0;

        RiskGrade grade = RiskGrade.fromScore(score);
        return new RiskResult(score, grade);
    }

    /**
     * 동의 항목이 여러 개일 때 대표 위험도(최고 점수 기준)를 산출한다.
     * 기업 대시보드에 표시할 단일 위험도 값으로 사용.
     *
     * @param inputs 동의 항목별 RiskInput 목록
     * @return 가장 높은 점수를 가진 RiskResult
     */
    public static RiskResult calculateMax(java.util.List<RiskInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("입력값 목록이 비어있습니다.");
        }
        return inputs.stream()
                .map(RiskCalculator::calculate)
                .max(java.util.Comparator.comparingDouble(RiskResult::getScore))
                .orElseThrow();
    }

    /**
     * 점수 차이로 "동의 철회 효과"를 계산한다.
     * 원본 입력 vs 특정 항목 동의 철회 후 입력을 비교해 감소 점수 반환.
     *
     * @param original 철회 전 RiskInput
     * @param afterRevocation 철회 후 RiskInput
     * @return 위험도 감소 점수 (양수 = 안전해짐)
     */
    public static double calculateRevocationEffect(RiskInput original, RiskInput afterRevocation) {
        double before = calculate(original).getScore();
        double after  = calculate(afterRevocation).getScore();
        return Math.round((before - after) * 10.0) / 10.0;
    }

    private static void validateInput(RiskInput input) {
        Objects.requireNonNull(input, "RiskInput이 null입니다.");
        Objects.requireNonNull(input.getDataSensitivity(), "DS(DataSensitivity)가 null입니다.");
        Objects.requireNonNull(input.getExposureScope(),   "ES(ExposureScope)가 null입니다.");
        Objects.requireNonNull(input.getTimeFactor(),      "TF(TimeFactor)가 null입니다.");
        Objects.requireNonNull(input.getPurposeClarity(),  "PC(PurposeClarity)가 null입니다.");
        Objects.requireNonNull(input.getAiRiskFactor(),    "AI(AiRiskFactor)가 null입니다.");
    }
}
