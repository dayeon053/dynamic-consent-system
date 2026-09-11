-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DML 스크립트
-- docs/crawling-targets-40-spec.md, docs/company_seed_candidates.csv 기준으로 크롤링 대상
-- 기업을 5개 → 40개로 확장하는 작업의 1단계: company 테이블에 신규 기업 시드 데이터를 넣는다.
--
-- 대상 선정 기준:
--  1) company_seed_candidates.csv에서 status="unverified"인 6개 행(티빙/웨이브/지니뮤직/
--     여기어때/에브리타임/우티)은 privacy_url이 없어 크롤링 자체가 불가능하므로 제외한다.
--     (관리자 콘솔 POST /admin/companies로 URL 확인 후 개별 등록하는 별도 TODO로 남김)
--  2) 나머지 기업 중 기존 5개 기업(카카오/네이버/배달의민족/토스/당근마켓)과 회사명이
--     같은 행(카카오(카카오톡)/네이버(공통) 포함)은 중복이므로 제외한다 — 아래 각 INSERT의
--     WHERE NOT EXISTS 가드가 재실행 시에도 이 스킵을 보장한다(멱등).
--  3) category/legal_name은 Company 엔티티가 NOT NULL로 요구하지만 CSV/스펙 문서에는 없는
--     컬럼이라, 공개된 사업자 정보를 기준으로 이번에 새로 조사해 채운 값이다(V6 사례와 동일하게
--     플레이스홀더 성격 — 실제 값은 각 팀 최종 확인 필요). isms_certified는 스펙 문서에 기재된
--     조사 시점 스냅샷 값을 그대로 사용했다.
--
-- 이 스크립트가 실행된 뒤의 실제 크롤링 성공/실패 결과는 PR 설명 및
-- docs/crawling_targets_expansion_report.md 참고.

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '네이버페이', '네이버파이낸셜 주식회사', '금융(간편결제)', 'com.nhn.android.naverpay', 'https://policy.naver.com/policy/privacy.html', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '네이버페이');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '인스타그램', 'Meta Platforms, Inc.', 'SNS', 'com.instagram.android', 'https://privacycenter.instagram.com/policy', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '인스타그램');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '11번가', '십일번가 주식회사', '이커머스', 'com.elevenst', 'https://privacy.11st.co.kr/', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '11번가');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT 'G마켓', '주식회사 지마켓', '이커머스', 'com.ebay.kr.gmarket', 'https://policy.gmarket.co.kr/terms-policy/privacy', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = 'G마켓');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '옥션', '주식회사 지마켓', '이커머스', 'com.ebay.kr.auction', 'https://policy.auction.co.kr/terms-policy/privacy', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '옥션');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT 'SSG닷컴', '에스에스지닷컴 주식회사', '이커머스', 'com.ssg.serviceapp.android.egiftcertificate', 'https://member.ssg.com/comm/privacy/intgInfo.ssg', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = 'SSG닷컴');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '카카오페이', '카카오페이 주식회사', '금융(간편결제)', 'com.kakaopay.app', 'https://www.kakaopay.com/terms/privacy', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '카카오페이');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '카카오뱅크', '주식회사 카카오뱅크', '금융(은행)', 'com.kakaobank.channel', 'https://m.kakaobank.com/PrivacyPolicy;ctg=privacyContractCompany', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '카카오뱅크');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '요기요', '주식회사 위대한상상', '배달', 'com.fineapp.yogiyo', 'https://www.yogiyo.co.kr/media/static/terms/p/20241106.html', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '요기요');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '쿠팡', '쿠팡 주식회사', '이커머스', 'com.coupang.mobile', 'https://privacy.coupang.com/ko/center/coupang/', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '쿠팡');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '카카오T', '카카오모빌리티 주식회사', '모빌리티', 'com.kakao.taxi', 'https://policy.kakaomobility.com/viewer/?pageCode=PRIVACY_POLICY&subPageCode=index&version=101', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '카카오T');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '쏘카', '주식회사 쏘카', '모빌리티(카셰어링)', 'socar.Socar', 'https://page.modu.kr/policy/privacy', false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '쏘카');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '유튜브', 'Google LLC', 'OTT(동영상)', 'com.google.android.youtube', 'https://policies.google.com/privacy?hl=ko', false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '유튜브');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '디시인사이드', '주식회사 디시인사이드', '커뮤니티', NULL, 'https://nstatic.dcinside.com/dc/m/policy/privacy.html', false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '디시인사이드');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '야놀자(NOL)', '야놀자 주식회사', '여가/숙박', 'com.yanolja.mobile', 'https://policy.yanolja.com/?t=privacy', false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '야놀자(NOL)');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '마켓컬리', '주식회사 컬리', '이커머스(신선식품)', 'com.kurly.customerapp', 'https://www.kurly.com/user-terms/privacy-policy', false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '마켓컬리');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '지그재그', '주식회사 카카오스타일', '패션이커머스', 'com.croquis.zigzag', 'https://cf.res.s.zigzag.kr/terms/user/privacy-20210118/index.html', false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '지그재그');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '알리익스프레스', 'Alibaba.com Singapore E-Commerce Private Limited', '이커머스(해외직구)', 'com.alibaba.aliexpresshd', 'https://cdn.contract.alibaba.com/terms/privacy_policy_full/20240624154655415/20240624154655415.html', false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '알리익스프레스');

