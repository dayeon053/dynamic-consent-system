package com.consentradar.consentradar.api.dto;

import java.math.BigDecimal;

/** PATCH /users/{userId}/consents/{consentItemId} 응답 */
public class ConsentPatchResponse {

    private final Long consentItemId;
    private final boolean isChecked;
    private final BigDecimal newRiskScore;
    private final String newRiskGrade;

    public ConsentPatchResponse(Long consentItemId, boolean isChecked,
                                BigDecimal newRiskScore, String newRiskGrade) {
        this.consentItemId = consentItemId;
        this.isChecked     = isChecked;
        this.newRiskScore  = newRiskScore;
        this.newRiskGrade  = newRiskGrade;
    }

    public Long       getConsentItemId() { return consentItemId; }
    public boolean    isChecked()        { return isChecked; }
    public BigDecimal getNewRiskScore()  { return newRiskScore; }
    public String     getNewRiskGrade()  { return newRiskGrade; }
}
