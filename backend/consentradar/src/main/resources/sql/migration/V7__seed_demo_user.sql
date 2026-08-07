-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DDL 스크립트
-- 데모 계정(user_id=1) 시드.
--
-- 배경: 인증이 아직 없어(PoC 단계, SecurityConfig가 permitAll) 프론트가 user_id=1을
-- 하드코딩해서 쓰고 있다(PATCH /users/1/consents/{id} 등). 이 계정이 로컬 DB에 없으면
-- "사용자를 찾을 수 없습니다"로 400이 난다. 지금까지는 팀원 각자 로컬에 수동으로
-- INSERT해야 했는데, 코드로 커밋해서 누구 로컬이든 이 스크립트 한 번이면 되게 한다.
--
-- INSERT IGNORE: 이미 user_id=1이 있거나(PK 충돌) 같은 이메일이 이미 있으면(email UNIQUE
-- 충돌) 조용히 건너뛴다 — 재실행해도 안전하다.
INSERT IGNORE INTO users (user_id, email, password, nickname, created_at)
VALUES (1, 'demo@dynamicconsent.com', 'demo-password-placeholder', '데모유저', NOW());
