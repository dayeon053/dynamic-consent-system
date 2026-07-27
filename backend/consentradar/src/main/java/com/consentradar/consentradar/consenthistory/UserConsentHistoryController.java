package com.consentradar.consentradar.consenthistory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserConsentHistoryController {

    private final UserConsentHistoryService userConsentHistoryService;

    public UserConsentHistoryController(UserConsentHistoryService userConsentHistoryService) {
        this.userConsentHistoryService = userConsentHistoryService;
    }

    /**
     * GET /users/{userId}/consents/history
     * 사용자의 동의 변경 이력을 변경 시각 오름차순으로 반환한다.
     */
    @GetMapping("/users/{userId}/consents/history")
    public List<UserConsentHistoryItemDto> getHistory(@PathVariable Long userId) {
        return userConsentHistoryService.getHistory(userId);
    }
}