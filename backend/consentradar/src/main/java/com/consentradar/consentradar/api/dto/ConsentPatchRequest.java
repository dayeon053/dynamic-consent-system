package com.consentradar.consentradar.api.dto;

/**
 * PATCH /users/{userId}/consents/{consentItemId} 요청 본문.
 *
 * checked 필드로 원하는 상태를 명시하면 서버가 그 값을 그대로 저장한다(멱등).
 * 본문 자체가 없거나(레거시 프론트 호출) checked가 null이면, 하위호환을 위해
 * 기존 반전(toggle) 방식으로 동작한다({@link com.consentradar.consentradar.api.ConsentApiService#toggleConsent}).
 */
public class ConsentPatchRequest {

    private Boolean checked;

    public ConsentPatchRequest() {
    }

    public ConsentPatchRequest(Boolean checked) {
        this.checked = checked;
    }

    public Boolean getChecked() {
        return checked;
    }

    public void setChecked(Boolean checked) {
        this.checked = checked;
    }
}
