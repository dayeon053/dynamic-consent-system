-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DML 스크립트
-- V12에서 추가한 27개 기업 중 크롤링이 실패했던 곳 일부를, 사용자가 직접 조사한 대체 URL로
-- 교체하는 2차 검증 결과를 반영한다. 상세 근거는 docs/crawling_targets_expansion_report.md
-- "2차 교체 검증" 섹션 참고.
--
-- 이번에 적용하는 범위: 크롤링 + LLM 파싱 + 위험도 산출까지 전부 실제로 성공을 확인한
-- 2건만 반영한다 (쏘카 → NH농협은행, 마켓컬리 → 하나은행).
--
-- 아래는 크롤링은 성공했지만 이번 스크립트에 포함하지 않은 것들 — 근거:
--   - SK텔레콤(페이스북 대체 후보, https://privacy.sktelecom.com/view.do?ctg=policy&name=policy):
--     크롤링 260,889자 성공. 그러나 LLM 호출 시 "context_length_exceeded"(151,315 토큰 >
--     gpt-4o-mini 한도 128,000 토큰)로 위험도 산출 실패. 여러 계열사/서비스 정책을 한
--     페이지에 모두 포함하고 있어 텍스트 자체가 너무 크다(스펙 문서 3-4번 이슈와 동일 유형).
--   - KT(무신사 대체 후보, https://inside.kt.com/html/privacy/privacy23.html): 크롤링
--     257,008자(헤드리스) 성공. LLM 호출 시 OpenAI 분당 토큰 한도(TPM 200,000) 초과로 실패.
--     텍스트 크기가 SK텔레콤과 비슷해 재시도해도 컨텍스트 한도 문제가 반복될 가능성이 높다.
--   - 우리은행(오늘의집 대체 후보, https://spot.wooribank.com/pot/Dream?withyou=CQSCT0051):
--     크롤링 267,556자 성공. LLM 호출 시 OpenAI 분당 토큰 한도 초과로 실패. 역시 텍스트가
--     방대해 동일 문제가 반복될 가능성이 높다.
--   → 세 곳 모두 "크롤링은 되지만 위험도 산출은 아직 안 되는" 상태이므로, 기존 페이스북/무신사/
--     오늘의집 row는 삭제하지 않고 그대로 남겨둔다(대체 후보가 완전히 검증되기 전까지).
--     본문을 섹션 단위로 분할해 요약 후 LLM에 넣는 등의 청킹 전략이 필요해 보이며, 이번
--     범위에서는 다루지 않는다.
--
-- 라인(신규 URL 후보 https://terms2.line.me/GlobalNFTWallet_Privacy?lang=ko)은 실제로는 LINE
-- NEXT Inc.의 DOSI(NFT 지갑) 서비스 개인정보처리방침으로 확인되어(우리 DB의 "라인"이 가리키는
-- LINE 메신저 앱과 다른 서비스), 이번 스크립트에 반영하지 않았다. 올바른 URL 재확인 필요.

DELETE FROM company WHERE company_name = '쏘카';
DELETE FROM company WHERE company_name = '마켓컬리';

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT 'NH농협은행', '농협은행 주식회사', '금융(은행)', 'nh.smart', 'https://m.nonghyup.com/servlet/PMECP1020R.view', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = 'NH농협은행');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '하나은행', '주식회사 하나은행', '금융(은행)', 'com.hanabank.ebk.channel.android.hananbank', 'https://www.kebhana.com/cont/customer/customer06/customer0604/index.jsp', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '하나은행');
