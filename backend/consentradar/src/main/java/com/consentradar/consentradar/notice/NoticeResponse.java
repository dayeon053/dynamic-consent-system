package com.consentradar.consentradar.notice;

import com.consentradar.consentradar.entity.PolicySnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * GET /notices 응답 항목 1건.
 *
 * [실측 확인] record가 아니라 일반 class + getter(`isChanged()`)로 만들어도 Jackson은
 * 여전히 "is" 접두사를 벗겨 프로퍼티명을 "changed"로 직렬화한다(record 컴포넌트 접근자와
 * 동일한 동작 — {@code UserConsentHistoryItemDto.isChecked()}도 같은 이유로 "checked"가
 * 아니라 "isChecked"로 나오는 게 아니라, 실은 record는 컴포넌트명을 그대로 쓰는 반면 class
 * getter는 반대로 깎인다). 실제로 `GET /notices` 호출 결과 `"changed":false`로 나오는 것을
 * 확인했고, 명세대로 "isChanged" 그대로 나오게 하려면 {@link JsonProperty}로 이름을
 * 명시적으로 고정해야 한다.
 */
public class NoticeResponse {

    private final Long companyId;
    private final String companyName;
    private final LocalDateTime crawledAt;
    private final boolean changed;

    public NoticeResponse(Long companyId, String companyName, LocalDateTime crawledAt, boolean changed) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.crawledAt = crawledAt;
        this.changed = changed;
    }

    public static NoticeResponse from(PolicySnapshot snapshot) {
        return new NoticeResponse(
                snapshot.getCompany().getCompanyId(),
                snapshot.getCompany().getCompanyName(),
                snapshot.getCrawledAt(),
                snapshot.isChanged());
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public LocalDateTime getCrawledAt() {
        return crawledAt;
    }

    @JsonProperty("isChanged")
    public boolean isChanged() {
        return changed;
    }
}
