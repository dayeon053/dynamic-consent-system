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
--
-- [2026-08-27 수정] 재실행 가능(idempotent)하도록 변경 — 이 컬럼은 실제로는 이 스크립트가
-- 아니라 ConsentItem.java의 @Column(columnDefinition="BIT(1) DEFAULT 1") + ddl-auto:update가
-- 만든다(이 프로젝트는 Flyway/Liquibase 미사용, 위 1번째 줄 참고). 즉 로컬 DB에 이미
-- is_active가 존재하는 상태에서 이 스크립트를 참고용으로 재실행해도(예: 신규 팀원이
-- 마이그레이션 이력을 훑어보다가 실수로 재실행) 에러 없이 안전하게 넘어가야 한다.
-- "ADD COLUMN IF NOT EXISTS"는 MariaDB 전용 확장이라 MySQL(Oracle, 이 프로젝트가 쓰는
-- mysql:8.0)에서는 그대로 문법 오류(1064)가 난다는 걸 직접 확인했다 — 그래서 MySQL에서
-- 이식 가능한 information_schema 조건부 + 동적 SQL(PREPARE/EXECUTE) 패턴을 쓴다.
SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'consent_item'
      AND COLUMN_NAME = 'is_active'
);

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE consent_item ADD COLUMN is_active BIT(1) NOT NULL DEFAULT 1',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
