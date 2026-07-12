package com.dynamicconsent.llm.dto;

import com.dynamicconsent.model.RiskInput;
import com.dynamicconsent.model.variable.AiRiskFactor;
import com.dynamicconsent.model.variable.DataSensitivity;
import com.dynamicconsent.model.variable.ExposureScope;
import com.dynamicconsent.model.variable.PurposeClarity;
import com.dynamicconsent.model.variable.TimeFactor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LLM이 반환하는 동의 항목 1개의 분석 결과 DTO
 *
 * LLM 출력 JSON에서 consentItems 배열의 원소 1개에 대응한다.
 *
 * ds/es/tf/pc/ai 필드는 각 Enum의 name() 문자열이어야 한다:
 *   ds  → "LOW" | "MODERATE" | "HIGH"
 *   es  → "LOW" | "MEDIUM"   | "HIGH"
 *   tf  → "SHORT" | "MEDIUM" | "LONG"
 *   pc  → "COMPLIANT" | "NON_COMPLIANT"
 *   ai  → "LOW_RISK"  | "HIGH_RISK"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsentItemAnalysis {

    @JsonProperty("itemName")
    private String itemName;

    /** "REQUIRED"(필수) 또는 "OPTIONAL"(선택) */
    @JsonProperty("itemType")
    private String itemType;

    @JsonProperty("ds")
    private String ds;

    @JsonProperty("es")
    private String es;

    @JsonProperty("tf")
    private String tf;

    @JsonProperty("pc")
    private String pc;

    @JsonProperty("ai")
    private String ai;

    /** LLM이 각 변수를 왜 그 값으로 판단했는지 근거 (DB 저장 및 UI 표시용) */
    @JsonProperty("dsReason")
    private String dsReason;

    @JsonProperty("esReason")
    private String esReason;

    @JsonProperty("tfReason")
    private String tfReason;

    @JsonProperty("pcReason")
    private String pcReason;

    @JsonProperty("aiReason")
    private String aiReason;

    /**
     * 이 DTO를 RiskCalculator에 넘길 수 있는 RiskInput으로 변환한다.
     * Enum.valueOf()는 대소문자를 구분하므로 LlmResponseParser에서 대문자 정규화 후 호출.
     */
    public RiskInput toRiskInput() {
        return new RiskInput(
                DataSensitivity.valueOf(ds),
                ExposureScope.valueOf(es),
                TimeFactor.valueOf(tf),
                PurposeClarity.valueOf(pc),
                AiRiskFactor.valueOf(ai)
        );
    }

    public String getItemName()  { return itemName; }
    public String getItemType()  { return itemType; }
    public String getDs()        { return ds; }
    public String getEs()        { return es; }
    public String getTf()        { return tf; }
    public String getPc()        { return pc; }
    public String getAi()        { return ai; }
    public String getDsReason()  { return dsReason; }
    public String getEsReason()  { return esReason; }
    public String getTfReason()  { return tfReason; }
    public String getPcReason()  { return pcReason; }
    public String getAiReason()  { return aiReason; }

    /** 파서에서 대문자 정규화 후 세팅 */
    public void setDs(String ds) { this.ds = ds; }
    public void setEs(String es) { this.es = es; }
    public void setTf(String tf) { this.tf = tf; }
    public void setPc(String pc) { this.pc = pc; }
    public void setAi(String ai) { this.ai = ai; }
}
