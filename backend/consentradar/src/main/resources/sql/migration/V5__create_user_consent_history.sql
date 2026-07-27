-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DDL 스크립트
-- 동의 상태 변경 이력을 append-only로 남기는 user_consent_history 테이블 신규 생성.
--
-- 배경: user_consent_check는 (user_id, consent_item_id) 조합당 단일 row로 "현재 상태"만
-- 저장하고 changed_at도 매번 덮어써서 이력이 남지 않는다. 동의 변경 이력 조회 API를 위해
-- 별도 테이블을 두고, 상태가 바뀔 때마다 user_consent_check(현재 상태)와 이 테이블
-- (이력) 양쪽에 동시에 기록한다(UserConsentHistoryRecorder). 개인 맞춤 위험도 계산 로직은
-- 그대로 user_consent_check만 조회하므로 회귀 위험이 없다.

CREATE TABLE user_consent_history (
    history_id       BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    consent_item_id   BIGINT       NOT NULL,
    is_checked        BOOLEAN      NOT NULL,
    changed_at        DATETIME     NOT NULL,
    PRIMARY KEY (history_id),
    CONSTRAINT fk_user_consent_history_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_user_consent_history_consent_item
        FOREIGN KEY (consent_item_id) REFERENCES consent_item (consent_item_id)
);

CREATE INDEX idx_user_consent_history_user_changed_at
    ON user_consent_history (user_id, changed_at);