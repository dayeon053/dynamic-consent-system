-- Flyway/Liquibase 미사용 (ddl-auto: update로 운영 중) — 참고/운영 반영용 DML 스크립트
-- 크롤링 대상 기업을 40개로 확장하는 작업의 마지막 단계: V12/V13 이후 23개 기업에
-- 아래 17개를 추가해 정확히 40개를 채운다.
--
-- 검증 절차 (V12/V13과 달리, 이번엔 크롤링 성공 여부뿐 아니라 실제
-- 크롤링→LLM(gpt-4o-mini)→위험도산출 전체 파이프라인을 별도 검증 스크립트
-- (backend/consentradar/src/test/java/.../verify/VerificationRunner.java,
-- `./gradlew verifyLlmPipeline`)로 직접 호출해 확인했다. DB 저장 없이 크롤링 텍스트를
-- 실제 LlmPromptTemplate/LlmClient에 넣어 consentItems가 정상 파싱되는지까지 검증.
--
-- 후보 19곳 중 2곳은 원문이 gpt-4o-mini 컨텍스트 한도(128,000 토큰)를 초과해 제외했다:
--   - 우리은행: 267,763자 → 153,713 토큰 → 400 context_length_exceeded
--   - SK텔레콤: 261,474자 → 150,695 토큰 → 429 rate_limit_exceeded(TPM 200,000 한도)
--     (둘 다 프롬프트 오버헤드를 얹기 전부터 이미 한도 초과 상태였음. 청킹/요약
--     전처리 없이는 반영 보류 — docs/known_issues.md 기존 기록과 동일한 유형의 이슈)
-- DB손해보험(idbins.com)은 이전 단계(로컬 조사)에서 robots.txt가 요청 경로를 차단해
-- 크롤링 검증 자체를 통과하지 못했으므로 애초에 후보에서 제외했다.
--
-- 아래 17곳은 전부 실제 위험도 산출(1개 이상의 consentItems)까지 성공을 확인했다.
-- 단, 2곳은 커버리지에 한계가 있어 참고 바람:
--   - 키움증권: 정적 크롤링(Jsoup)이 법정 전문이 아니라 "인포그래픽 요약" 탭만 가져온다
--     (3,343자). LLM은 이 요약본만으로 3개 항목을 뽑아냈지만, 법정 전문 대비 커버리지가
--     얕을 수 있음 — 전문 탭의 실제 URL을 확인하는 후속 작업 필요.
--   - SC제일은행: 이 URL은 종합 개인정보처리방침이 아니라 "행태정보 수집·이용" 조항만
--     다루는 별도 문서다(1,823자, 1개 항목만 추출). 종합 방침 URL을 별도로 찾아 교체하는
--     후속 작업 필요.

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '미래에셋증권', '미래에셋증권 주식회사', '금융(증권)', 'com.miraeasset.trade', 'https://securities.miraeasset.com/hki/hki3040/n02.do', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '미래에셋증권');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT 'SK브로드밴드', '에스케이브로드밴드 주식회사', '통신(인터넷/IPTV)', 'com.sk.btvmobile', 'https://www.bworld.co.kr/footer/protect.do?menu_id=F01030000', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = 'SK브로드밴드');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '삼성생명', '삼성생명보험 주식회사', '금융(생명보험)', 'com.samsunglife.direct', 'https://family.samsunglife.com/pc/privacy/withPrivacy.html', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '삼성생명');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT 'KB증권', '주식회사 케이비증권', '금융(증권)', 'com.kbsec.mts.mtsvi', 'https://www.kbsec.com/common/agree/private03.html', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = 'KB증권');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '키움증권', '주식회사 키움증권', '금융(증권)', 'com.kiwoom.mobile', 'https://www.kiwoom.com/h/customer/financeguide/VPersonalDataPolicyView', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '키움증권');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT 'DB생명', 'DB생명보험 주식회사', '금융(생명보험)', 'com.idblife.smartapp', 'https://www.idblife.com/auth/web/fnl_private', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = 'DB생명');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT 'KB손해보험', 'KB손해보험 주식회사', '금융(손해보험)', 'com.kbinsure.smart', 'http://www.kbinsure.co.kr/CU105020001.ec', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = 'KB손해보험');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '하나카드', '주식회사 하나카드', '금융(카드)', 'com.hanacard.paycla', 'https://www.hanacard.co.kr/OSA75350000N.web?schID=scd&mID=OSA75350000N', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '하나카드');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT 'IBK투자증권', '아이비케이투자증권 주식회사', '금융(증권)', 'com.ibks.smart', 'https://m.ibks.com/ikd/IKD070101.do', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = 'IBK투자증권');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '대신증권', '대신증권 주식회사', '금융(증권)', 'com.daishin.cybosviewer', 'https://www.daishin.com/g.ds?m=6877&p=6499&v=5461', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '대신증권');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '케이뱅크', '주식회사 케이뱅크', '금융(인터넷은행)', 'com.kbanknow', 'https://www.kbanknow.com/ib20/mnu/CBRCSC090100', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '케이뱅크');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT 'SC제일은행', '한국스탠다드차타드은행 주식회사', '금융(은행)', 'kr.co.standardchartered.mobilebanking', 'https://www.standardchartered.co.kr/np/kr/cms/cm/cc/IndividualPolicyTreatment_s2.jsp', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = 'SC제일은행');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '부산은행', '주식회사 비엔케이부산은행', '금융(은행)', 'com.knb.psb', 'https://m.busanbank.co.kr/ib20/mnu/MWPCSCE000CSC10', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '부산은행');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '흥국생명', '흥국생명보험 주식회사', '금융(생명보험)', 'com.heungkuklife.direct', 'https://www.heungkuklife.co.kr/front/service/policy/handlePersonalInf.do', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '흥국생명');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '메리츠화재', '메리츠화재해상보험 주식회사', '금융(손해보험)', 'com.meritzfire.direct', 'https://www.meritzfire.com/default/personal_info_policy.html', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '메리츠화재');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '새마을금고중앙회', '새마을금고중앙회', '금융(상호금융)', 'kr.co.kfcc.mgnotify', 'https://kfcc.co.kr/etc/etc0101.do', false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '새마을금고중앙회');

INSERT INTO company (company_name, legal_name, category, package_name, privacy_url, isms_certified, created_at, updated_at)
SELECT '코레일톡', '한국철도공사', '교통(철도)', 'com.korail.talk', 'http://smart.letskorail.com/docs/guide/privacy.html', false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM company WHERE company_name = '코레일톡');
