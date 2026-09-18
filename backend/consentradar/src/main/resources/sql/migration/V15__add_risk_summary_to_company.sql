-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DDL 스크립트
-- company에 사용자용 위험 요약 한 문장 컬럼을 추가한다 (nullable TEXT).
--
-- 배경: 멘토 피드백에 따라 기업 목록/상세/팝업(GET /companies)에 "어떤 정보를 어떤 목적으로
-- 가져가서 위험한지"를 요약한 한 문장을 노출해야 한다. 기존 dsReason/esReason/... (V11)은
-- ConsentItem(동의 항목) 단위 근거라 화면 성격이 다르고, 이건 기업 단위 요약이라 별도
-- 컬럼으로 Company에 둔다. LlmPromptTemplate이 riskSummary를 함께 생성하고
-- RiskPipelineService.analyzeAndSaveRisk()가 저장한다.
--
-- 기존 row와 아직 한 번도 분석되지 않은 기업은 이 컬럼이 NULL이다 — 프론트는 null이면 설명
-- 칸을 숨긴다. nullable이라 백필 없이도 안전하다.
--
-- 번호가 V12가 아니라 V15인 이유: 이 스크립트를 작성하는 시점에 V12~V14는
-- feature/crawling-targets-expansion(PR #54, 크롤링 대상 확장)이 아직 develop에 머지되지
-- 않은 채로 이미 점유하고 있다. 번호 충돌을 피하기 위해 그 다음 번호를 사용한다.
--
-- 이 컬럼은 실제로는 이 스크립트가 아니라 Company.java의 @Column(columnDefinition="TEXT") +
-- ddl-auto:update가 만든다(V9/V11과 동일한 패턴). 이 스크립트는 재실행 가능(idempotent)한
-- 참고용 DDL이다.
SET @risk_summary_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'company' AND COLUMN_NAME = 'risk_summary'
);
SET @ddl = IF(@risk_summary_exists = 0, 'ALTER TABLE company ADD COLUMN risk_summary TEXT', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
