package com.consentradar.consentradar.consenthistory;

import com.consentradar.consentradar.entity.ConsentItem;
import com.consentradar.consentradar.entity.User;
import com.consentradar.consentradar.entity.UserConsentHistory;
import com.consentradar.consentradar.repository.UserConsentHistoryRepository;
import org.springframework.stereotype.Component;

/**
 * 동의 상태가 바뀔 때마다 {@link UserConsentHistory}에 append-only로 한 건씩 기록한다.
 *
 * {@link com.consentradar.consentradar.api.ConsentApiService#toggleConsent}가
 * UserConsentCheck를 upsert한 직후, 같은 트랜잭션 안에서 이 컴포넌트를 호출한다 — 별도
 * 트랜잭션을 열지 않으므로 호출자의 트랜잭션에 그대로 참여하고, 토글 자체가 롤백되면
 * 이력 기록도 함께 롤백된다.
 */
@Component
public class UserConsentHistoryRecorder {

    private final UserConsentHistoryRepository userConsentHistoryRepository;

    public UserConsentHistoryRecorder(UserConsentHistoryRepository userConsentHistoryRepository) {
        this.userConsentHistoryRepository = userConsentHistoryRepository;
    }

    public void record(User user, ConsentItem consentItem, boolean isChecked) {
        UserConsentHistory history = new UserConsentHistory();
        history.setUser(user);
        history.setConsentItem(consentItem);
        history.setChecked(isChecked);
        userConsentHistoryRepository.save(history);
    }
}