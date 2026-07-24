-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DDL 스크립트
-- Sprint03 태스크 2-4: 사용자별 개인 맞춤 대표 위험도(user_id NOT NULL)가 같은 날짜에
-- 중복 저장되는 것을 방지한다 (append-only, 하루 1건).
--
-- user_id가 NULL인 배치 파이프라인(RiskPipelineService) row는 MySQL이 NULL을 서로 다른
-- 값으로 취급하므로(NULL <> NULL) 이 제약에 영향받지 않는다. 즉 배치가 같은 날 같은
-- company_id로 여러 항목별(is_representative=false) row를 저장하는 기존 동작은 그대로 유지된다.

ALTER TABLE risk_score
    ADD CONSTRAINT uq_risk_score_user_company_scoredat_rep
    UNIQUE (user_id, company_id, scored_at, is_representative);
