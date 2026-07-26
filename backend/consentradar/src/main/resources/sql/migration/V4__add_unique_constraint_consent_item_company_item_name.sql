-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DDL 스크립트
-- consent_item(company_id, item_name)에 UNIQUE 제약 추가.
--
-- 배경: RiskPipelineService.analyzeAndSaveRisk()가 itemName 기준으로 기존 ConsentItem을
-- 찾아 upsert하도록 고쳤지만(PR #24), 애플리케이션 레벨의 "조회 후 insert" 로직만으로는
-- 두 요청이 거의 동시에 들어오는 경우(예: 인증 없는 관리자 수동 트리거 API 더블클릭,
-- 또는 스케줄러 실행과 겹침)를 막을 수 없다. DB에 이 unique 제약이 있어야 동시 요청에서도
-- 실제로 중복 row가 생기지 않는다는 게 보장된다.
--
-- 적용 전에 이미 중복된 (company_id, item_name) 조합이 있다면 이 ALTER는 실패한다.
-- 적용 전에 아래 쿼리로 먼저 확인할 것:
--   SELECT company_id, item_name, COUNT(*) FROM consent_item GROUP BY company_id, item_name HAVING COUNT(*) > 1;
-- 중복이 있다면 어느 row를 남길지 정해서 정리한 뒤 이 스크립트를 적용한다.

ALTER TABLE consent_item
    ADD CONSTRAINT uk_consent_item_company_item_name UNIQUE (company_id, item_name);
