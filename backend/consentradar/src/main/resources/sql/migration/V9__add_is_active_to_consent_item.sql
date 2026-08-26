-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DDL 스크립트
-- consent_item에 is_active 컬럼을 추가한다 (소프트 삭제 플래그, 기본값 TRUE).
--
-- 배경: RiskPipelineService.analyzeAndSaveRisk()의 ConsentItem upsert(itemName 기준)는
-- 이번 크롤링 결과에 더 이상 나타나지 않는 예전 항목을 그대로 두고 있었다(TODO로 남아있던
-- 미결 사항). 그 결과 재분석 후에도 예전 mock/오래된 항목이 새 항목과 함께 남아
-- PersonalRiskCalculator.calculate()의 위험도 계산에 섞여 들어가는 문제가 있었다
-- (2026-08-26 수동 데이터 정리로 임시 확인됨).
--
-- 하드 삭제 대신 소프트 삭제를 택한 이유: UserConsentCheck/UserConsentHistory가
-- consent_item_id를 FK로 참조하고 있어, 하드 삭제하면 사용자의 동의 이력이 끊긴다.
-- is_active=false로 내리면 이력은 보존하면서 위험도 계산·동의 항목 목록 API에서는
-- 제외할 수 있다.
--
-- DEFAULT TRUE(=1)로 추가하므로 기존 row도 전부 즉시 active=true로 backfill된다 —
-- V6와 달리 nullable 단계를 거칠 필요 없다.
ALTER TABLE consent_item
    ADD COLUMN is_active BIT(1) NOT NULL DEFAULT 1;
