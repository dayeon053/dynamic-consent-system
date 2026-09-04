-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DDL 스크립트
-- company의 회사명(company_name)/법인명(legal_name)/카테고리(category)가 로컬 DB에
-- mojibake(문자 깨짐)로 저장되어 있는 경우 정상값으로 되돌린다.
--
-- 배경: 리뷰어(가현) 로컬 DB에서 company 5건(카카오/네이버/배달의민족/토스/당근마켓)의
-- 이름이 실제로 깨진 채 저장되어 있는 게 확인됐다(2026-08-27). 이 프로젝트는 각자
-- 로컬 MySQL을 쓰고(Flyway/Liquibase 없이 ddl-auto:update + 이 폴더의 SQL 스크립트를
-- 수동 적용하는 방식), V6에서 category/legal_name을 채운 이후 각자 환경에서 데이터가
-- 갈라진 것으로 보인다 — 우리 로컬 DB는 정상값이 저장되어 있는 것으로 확인됨. 원인은
-- 확정하지 못했지만(V6 스크립트를 utf8mb4가 아닌 커넥션 charset의 클라이언트로 실행했을
-- 가능성이 유력), 화면 표시만 깨지는 문제(known_issues.md의 콘솔 charset 이슈)와는 다르게
-- 이번엔 DB에 실제로 깨진 바이트가 저장된 경우다.
--
-- company_name 자체가 이미 깨져 있을 수 있어 그 컬럼으로 WHERE를 걸면 매칭이 안 되거나
-- 잘못된 row를 건드릴 위험이 있다. 대신 package_name(순수 영문 Android 패키지 식별자,
-- company.package_name UNIQUE 제약)으로 매칭한다 — 이 값은 ASCII라 인코딩 문제의 영향을
-- 받지 않는다.
--
-- UPDATE라 몇 번을 다시 실행해도 항상 같은 정상값으로 맞춰질 뿐이다 — 이미 정상인 로컬
-- DB에서 실행해도(각자 로컬 DB가 실제로 이미 정상인 게 이번에 확인된 상황처럼) 같은 값을
-- 다시 쓰는 것뿐이라 안전(idempotent)하다.

UPDATE company SET company_name = '카카오', legal_name = '(주)카카오', category = 'SNS'
    WHERE package_name = 'com.kakao.talk';

UPDATE company SET company_name = '네이버', legal_name = '네이버 주식회사', category = '포털'
    WHERE package_name = 'com.nhn.android.search';

UPDATE company SET company_name = '배달의민족', legal_name = '주식회사 우아한형제들', category = '배달'
    WHERE package_name = 'com.sampleapp';

UPDATE company SET company_name = '토스', legal_name = '(주)비바리퍼블리카', category = '금융'
    WHERE package_name = 'viva.republica.toss';

UPDATE company SET company_name = '당근마켓', legal_name = '(주)당근마켓', category = '중고거래'
    WHERE package_name = 'com.towneers.www';

-- 적용 후 아래 SELECT로 5건 모두 정상 표시되는지 눈으로 확인한다(터미널 클라이언트
-- charset이 utf8mb4가 아니면 여기서도 ?로 보일 수 있으니, 그 경우 known_issues.md의
-- 콘솔 charset 문제인지 먼저 구분할 것).
-- SELECT company_id, company_name, legal_name, category, package_name FROM company ORDER BY company_id;
