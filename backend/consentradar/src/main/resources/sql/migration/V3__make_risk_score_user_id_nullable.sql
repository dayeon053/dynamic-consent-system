-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DDL 스크립트
-- risk_score.user_id를 NULL 허용으로 변경.
--
-- 배경: RiskScore 엔티티는 이미 user_id를 nullable=true로 선언하고 있었지만(배치 파이프라인이
-- user=null로 기업 단위 대표/항목별 점수를 저장하는 용도), ddl-auto: update는 기존 컬럼의
-- NOT NULL 제약을 되돌리지 않기 때문에 로컬 DB 등 예전에 생성된 스키마에는 여전히 user_id가
-- NOT NULL로 남아 있을 수 있다.
--
-- 이 드리프트는 RiskPipelineService(크롤링→LLM→위험도산출→DB저장 전체 파이프라인)를 처음으로
-- 실제 DB에 대고 실행해봤을 때(Sprint03 파이프라인 실행 연결 작업, RiskPipelineServiceIntegrationTest)
-- 발견됐다. 그 전까지는 이 경로가 어디서도 호출되지 않는 죽은 코드였어서 실제 insert가 한 번도
-- 발생하지 않아 드러나지 않고 있었다.

ALTER TABLE risk_score
    MODIFY user_id BIGINT NULL;
