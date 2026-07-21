package com.consentradar.consentradar.api.dto;

import com.consentradar.consentradar.entity.ConsentItem;

/**
 * GET /companies/{companyId}/consent-items 응답 항목.
 * REQUIRED 항목은 checked 항상 true(항상 동의 상태), OPTIONAL은 이 사용자의 실제 체크 여부를 반영한다.
 */
public class ConsentItemResponse {

    private final Long consentItemId;
    private final String itemName;
    private final ConsentItem.ItemType itemType;
    private final boolean checked;
    private final int dsScore;
    private final int esScore;
    private final int tfScore;
    private final double pcScore;
    private final double aiScore;

    public ConsentItemResponse(ConsentItem item, boolean checked) {
        this.consentItemId = item.getConsentItemId();
        this.itemName      = item.getItemName();
        this.itemType      = item.getItemType();
        this.checked       = checked;
        this.dsScore       = item.getDsScore();
        this.esScore       = item.getEsScore();
        this.tfScore       = item.getTfScore();
        this.pcScore       = item.getPcScore();
        this.aiScore       = item.getAiScore();
    }

    public Long                 getConsentItemId() { return consentItemId; }
    public String               getItemName()      { return itemName; }
    public ConsentItem.ItemType getItemType()       { return itemType; }
    public boolean               isChecked()       { return checked; }
    public int                   getDsScore()       { return dsScore; }
    public int                   getEsScore()       { return esScore; }
    public int                   getTfScore()       { return tfScore; }
    public double                getPcScore()       { return pcScore; }
    public double                getAiScore()       { return aiScore; }
}
