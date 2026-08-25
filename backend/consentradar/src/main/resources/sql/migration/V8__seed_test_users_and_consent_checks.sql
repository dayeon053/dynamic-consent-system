-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DDL 스크립트
-- A-1 (api_spec_v2_final.md 배포 전 마지막 점검): PATCH 검증/개발용 테스트 유저 시드.
--
-- 배경: users 테이블이 비어 있으면 PATCH /users/{userId}/consents/{consentItemId}가
-- 100% "존재하지 않는 userId" 404(GlobalExceptionHandler 적용 후 기준 — 이전엔 500)로
-- 실패한다. V7이 데모 계정(user_id=1) 1명만 시드했던 것에 더해, 이 스크립트는 테스트용
-- 유저 3명(2,3,4)과 실제 존재하는 company_id(크롤링으로 이미 채워진 1~5)의 OPTIONAL
-- 동의 항목에 엮인 UserConsentCheck 더미 데이터를 추가해 개인 맞춤 위험도 계산까지
-- 바로 검증할 수 있게 한다.
--
-- INSERT IGNORE: user_id/email UNIQUE 충돌 시 조용히 건너뛴다 — 재실행해도 안전하다.
INSERT IGNORE INTO users (user_id, email, password, nickname, created_at)
VALUES
    (2, 'tester1@dynamicconsent.com', 'test-password-placeholder', '테스터1', NOW()),
    (3, 'tester2@dynamicconsent.com', 'test-password-placeholder', '테스터2', NOW()),
    (4, 'tester3@dynamicconsent.com', 'test-password-placeholder', '테스터3', NOW());

-- UserConsentCheck 더미 데이터.
--
-- consent_item_id는 크롤링 파이프라인(RiskPipelineService)이 LLM 분석 결과로 채우는
-- auto-increment 값이라 환경마다(크롤링 이력에 따라) 달라 하드코딩할 수 없다. 대신 각
-- company_id의 OPTIONAL 항목을 그때그때 조회해서 엮는다. consent_item이 아직 비어있는
-- 환경(크롤링을 한 번도 안 돌린 상태)에서는 SELECT가 0건이라 그냥 삽입 없이 넘어간다.
--
-- user_consent_check에는 (user_id, consent_item_id) UNIQUE 제약이 없어 INSERT IGNORE만으로는
-- 재실행 시 중복이 막히지 않는다 — 그래서 NOT EXISTS로 직접 중복을 막아 재실행해도 안전하게 한다.
INSERT INTO user_consent_check (user_id, consent_item_id, is_checked, changed_at)
SELECT 2, ci.consent_item_id, TRUE, NOW()
FROM consent_item ci
WHERE ci.company_id = 1 AND ci.item_type = 'OPTIONAL'
  AND NOT EXISTS (SELECT 1 FROM user_consent_check ucc WHERE ucc.user_id = 2 AND ucc.consent_item_id = ci.consent_item_id)
LIMIT 1;

INSERT INTO user_consent_check (user_id, consent_item_id, is_checked, changed_at)
SELECT 2, ci.consent_item_id, FALSE, NOW()
FROM consent_item ci
WHERE ci.company_id = 2 AND ci.item_type = 'OPTIONAL'
  AND NOT EXISTS (SELECT 1 FROM user_consent_check ucc WHERE ucc.user_id = 2 AND ucc.consent_item_id = ci.consent_item_id)
LIMIT 1;

INSERT INTO user_consent_check (user_id, consent_item_id, is_checked, changed_at)
SELECT 2, ci.consent_item_id, TRUE, NOW()
FROM consent_item ci
WHERE ci.company_id = 3 AND ci.item_type = 'OPTIONAL'
  AND NOT EXISTS (SELECT 1 FROM user_consent_check ucc WHERE ucc.user_id = 2 AND ucc.consent_item_id = ci.consent_item_id)
LIMIT 1;

INSERT INTO user_consent_check (user_id, consent_item_id, is_checked, changed_at)
SELECT 3, ci.consent_item_id, TRUE, NOW()
FROM consent_item ci
WHERE ci.company_id = 2 AND ci.item_type = 'OPTIONAL'
  AND NOT EXISTS (SELECT 1 FROM user_consent_check ucc WHERE ucc.user_id = 3 AND ucc.consent_item_id = ci.consent_item_id)
LIMIT 1;

INSERT INTO user_consent_check (user_id, consent_item_id, is_checked, changed_at)
SELECT 3, ci.consent_item_id, TRUE, NOW()
FROM consent_item ci
WHERE ci.company_id = 4 AND ci.item_type = 'OPTIONAL'
  AND NOT EXISTS (SELECT 1 FROM user_consent_check ucc WHERE ucc.user_id = 3 AND ucc.consent_item_id = ci.consent_item_id)
LIMIT 1;

INSERT INTO user_consent_check (user_id, consent_item_id, is_checked, changed_at)
SELECT 3, ci.consent_item_id, FALSE, NOW()
FROM consent_item ci
WHERE ci.company_id = 5 AND ci.item_type = 'OPTIONAL'
  AND NOT EXISTS (SELECT 1 FROM user_consent_check ucc WHERE ucc.user_id = 3 AND ucc.consent_item_id = ci.consent_item_id)
LIMIT 1;

INSERT INTO user_consent_check (user_id, consent_item_id, is_checked, changed_at)
SELECT 4, ci.consent_item_id, FALSE, NOW()
FROM consent_item ci
WHERE ci.company_id = 1 AND ci.item_type = 'OPTIONAL'
  AND NOT EXISTS (SELECT 1 FROM user_consent_check ucc WHERE ucc.user_id = 4 AND ucc.consent_item_id = ci.consent_item_id)
LIMIT 1;

INSERT INTO user_consent_check (user_id, consent_item_id, is_checked, changed_at)
SELECT 4, ci.consent_item_id, FALSE, NOW()
FROM consent_item ci
WHERE ci.company_id = 3 AND ci.item_type = 'OPTIONAL'
  AND NOT EXISTS (SELECT 1 FROM user_consent_check ucc WHERE ucc.user_id = 4 AND ucc.consent_item_id = ci.consent_item_id)
LIMIT 1;

INSERT INTO user_consent_check (user_id, consent_item_id, is_checked, changed_at)
SELECT 4, ci.consent_item_id, TRUE, NOW()
FROM consent_item ci
WHERE ci.company_id = 5 AND ci.item_type = 'OPTIONAL'
  AND NOT EXISTS (SELECT 1 FROM user_consent_check ucc WHERE ucc.user_id = 4 AND ucc.consent_item_id = ci.consent_item_id)
LIMIT 1;
