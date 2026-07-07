package com.dynamicconsent.model;

import com.dynamicconsent.model.variable.AiRiskFactor;
import com.dynamicconsent.model.variable.DataSensitivity;
import com.dynamicconsent.model.variable.ExposureScope;
import com.dynamicconsent.model.variable.PurposeClarity;
import com.dynamicconsent.model.variable.TimeFactor;

/**
 * 위험도 산출 입력값 모델 — 백엔드 DB 저장 및 앱 공용 사용
 *
 * 5대 변수를 하나의 객체로 묶어 전달하는 데이터 컨테이너.
 * 백엔드: DB Entity의 임베디드 값 객체 또는 별도 컬럼으로 저장
 * 프론트(Android): 기업별 동의 항목 분석 결과를 담는 모델 클래스
 */
public class RiskInput {

    /** DS: 데이터 민감도 */
    private final DataSensitivity dataS;

    /** ES: 노출 범위 */
    private final ExposureScope exposureScope;

    /** TF: 경과 시간(보관 기간) */
    private final TimeFactor timeFactor;

    /** PC: 목적 명확성 */
    private final PurposeClarity purposeClarity;

    /** AI: AI 위험계수 */
    private final AiRiskFactor aiRiskFactor;

    public RiskInput(DataSensitivity dataS, ExposureScope exposureScope,
                     TimeFactor timeFactor, PurposeClarity purposeClarity,
                     AiRiskFactor aiRiskFactor) {
        this.dataS = dataS;
        this.exposureScope = exposureScope;
        this.timeFactor = timeFactor;
        this.purposeClarity = purposeClarity;
        this.aiRiskFactor = aiRiskFactor;
    }

    public DataSensitivity getDataSensitivity()   { return dataS; }
    public ExposureScope   getExposureScope()      { return exposureScope; }
    public TimeFactor      getTimeFactor()         { return timeFactor; }
    public PurposeClarity  getPurposeClarity()     { return purposeClarity; }
    public AiRiskFactor    getAiRiskFactor()       { return aiRiskFactor; }
}
