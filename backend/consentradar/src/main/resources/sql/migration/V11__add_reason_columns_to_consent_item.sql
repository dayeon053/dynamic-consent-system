-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DDL 스크립트
-- consent_item에 5대 변수별 LLM 판단 근거 컬럼을 추가한다 (전부 nullable TEXT).
--
-- 배경: LlmPromptTemplate이 LLM에게 dsReason/esReason/tfReason/pcReason/aiReason(변수별
-- 판단 근거 한 문장)까지 만들어 달라고 요청하고 있었는데, RiskPipelineService.analyzeAndSaveRisk()가
-- 점수(ds/es/tf/pc/aiScore)만 뽑아 쓰고 이 텍스트는 그동안 저장하지 않고 버리고 있었다.
-- 멘토 피드백(상세 화면이 "왜 위험한지" 사용자 친화 설명 없이 점수·평가 항목부터 보여줘 와닿지
-- 않는다는 지적)에 따라, 이 근거 텍스트를 저장 + API로 노출해 프론트가 점수보다 먼저 보여줄 수
-- 있게 한다.
--
-- 기존 row는 이 컬럼들이 전부 NULL이다 — 재크롤링(다음 배치 또는 관리자 수동 트리거)이
-- 일어나기 전까지는 채워지지 않는다. nullable이라 백필 없이도 안전하다.
--
-- 이 컬럼들은 실제로는 이 스크립트가 아니라 ConsentItem.java의
-- @Column(columnDefinition="TEXT") + ddl-auto:update가 만든다(V9와 동일한 패턴). 이 스크립트는
-- 재실행 가능(idempotent)한 참고용 DDL이다.
SET @ds_reason_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consent_item' AND COLUMN_NAME = 'ds_reason'
);
SET @ddl = IF(@ds_reason_exists = 0, 'ALTER TABLE consent_item ADD COLUMN ds_reason TEXT', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @es_reason_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consent_item' AND COLUMN_NAME = 'es_reason'
);
SET @ddl = IF(@es_reason_exists = 0, 'ALTER TABLE consent_item ADD COLUMN es_reason TEXT', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @tf_reason_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consent_item' AND COLUMN_NAME = 'tf_reason'
);
SET @ddl = IF(@tf_reason_exists = 0, 'ALTER TABLE consent_item ADD COLUMN tf_reason TEXT', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @pc_reason_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consent_item' AND COLUMN_NAME = 'pc_reason'
);
SET @ddl = IF(@pc_reason_exists = 0, 'ALTER TABLE consent_item ADD COLUMN pc_reason TEXT', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ai_reason_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'consent_item' AND COLUMN_NAME = 'ai_reason'
);
SET @ddl = IF(@ai_reason_exists = 0, 'ALTER TABLE consent_item ADD COLUMN ai_reason TEXT', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
