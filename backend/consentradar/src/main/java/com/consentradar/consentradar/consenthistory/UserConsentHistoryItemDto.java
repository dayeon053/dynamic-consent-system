package com.consentradar.consentradar.consenthistory;

import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.UserConsentHistory;

import java.time.LocalDateTime;

/** GET /users/{userId}/consents/history 응답 항목 1건. */
public record UserConsentHistoryItemDto(
        Long consentItemId,
        String itemName,
        Long companyId,
        String companyName,
        boolean isChecked,
        LocalDateTime changedAt
) {
    public static UserConsentHistoryItemDto from(UserConsentHistory history) {
        ConsentItem item = history.getConsentItem();
        Company company = item.getCompany();
        return new UserConsentHistoryItemDto(
                item.getConsentItemId(),
                item.getItemName(),
                company.getCompanyId(),
                company.getCompanyName(),
                history.isChecked(),
                history.getChangedAt());
    }
}