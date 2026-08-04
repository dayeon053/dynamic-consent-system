-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DDL 스크립트
-- company에 category(기업 카테고리), legal_name(법인 정식 명칭) 컬럼을 추가하고 기존
-- 5개 기업(카카오/네이버/배달의민족/토스/당근마켓) 데이터를 backfill한다.
--
-- 배경: 기업상세 '정보' 탭(태스크 4-8)이 지금까지 프론트에서 category="기타",
-- legalName=companyName으로 하드코딩된 임시값을 쓰고 있었다(CompanyMapper.kt). 실제 값을
-- 내려주려면 서버 스키마에 두 컬럼이 있어야 한다.
--
-- V3와 같은 이유로 ddl-auto: update는 기존 컬럼을 NOT NULL로 되돌리지 못하므로, 이미 company
-- 행이 있는 로컬 DB에서는 반드시 1) nullable로 추가 → 2) 아래 backfill → 3) NOT NULL로 전환
-- 순서를 지켜야 한다(그냥 NOT NULL로 바로 추가하면 기존 행 때문에 ALTER 자체가 실패한다).
-- company 테이블에 아래 5개 외 다른 기업이 이미 있는 로컬 DB라면, 3번 실행 전에 그 기업들도
-- category/legal_name을 먼저 채워야 한다.

-- 1. nullable로 컬럼 추가
ALTER TABLE company
    ADD COLUMN category VARCHAR(50) NULL,
    ADD COLUMN legal_name VARCHAR(100) NULL;

-- 2. 기존 5개 기업 backfill (company_name 기준 매칭)
UPDATE company SET category = 'SNS',      legal_name = '(주)카카오'          WHERE company_name = '카카오';
UPDATE company SET category = '포털',     legal_name = '네이버 주식회사'      WHERE company_name = '네이버';
UPDATE company SET category = '배달',     legal_name = '주식회사 우아한형제들' WHERE company_name = '배달의민족';
UPDATE company SET category = '금융',     legal_name = '(주)비바리퍼블리카'    WHERE company_name = '토스';
UPDATE company SET category = '중고거래', legal_name = '당근마켓 주식회사'    WHERE company_name = '당근마켓';

-- 3. backfill 확인 후 NOT NULL로 전환
ALTER TABLE company
    MODIFY category VARCHAR(50) NOT NULL,
    MODIFY legal_name VARCHAR(100) NOT NULL;
