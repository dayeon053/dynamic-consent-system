package com.consentradar.consentradar.consenthistory;

import com.consentradar.consentradar.repository.UserConsentHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserConsentHistoryService {

    private final UserConsentHistoryRepository userConsentHistoryRepository;

    public UserConsentHistoryService(UserConsentHistoryRepository userConsentHistoryRepository) {
        this.userConsentHistoryRepository = userConsentHistoryRepository;
    }

    /** GET /users/{userId}/consents/history — 변경 시각 오름차순(오래된 순). */
    @Transactional(readOnly = true)
    public List<UserConsentHistoryItemDto> getHistory(Long userId) {
        return userConsentHistoryRepository.findByUserIdOrderByChangedAtAsc(userId)
                .stream()
                .map(UserConsentHistoryItemDto::from)
                .toList();
    }
}