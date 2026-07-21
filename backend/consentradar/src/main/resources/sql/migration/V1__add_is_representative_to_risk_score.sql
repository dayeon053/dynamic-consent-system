-- Flyway/Liquibase 미사용 (ddl-auto: create로 운영 중) — 참고/운영 반영용 DDL 스크립트
-- RiskScore: 항목별 점수(false)와 기업 대표(종합) 점수(true)를 구분하는 컬럼 추가

ALTER TABLE risk_score
    ADD COLUMN is_representative BOOLEAN NOT NULL DEFAULT FALSE
    AFTER scored_at;