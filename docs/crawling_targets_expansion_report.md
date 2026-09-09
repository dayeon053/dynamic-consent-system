# 크롤링 대상 기업 확장 (5개 → 32개) 1차 검증 보고서

> 작업일: 2026-09-09. 근거 문서: `crawling-targets-40-spec.md`, `company_seed_candidates.csv`
> (팀 전달용 로컬 파일, 저장소 미포함). 실행 파일: `backend/consentradar/src/main/resources/sql/migration/V12__seed_crawling_targets_expansion.sql`.

## 1. 요약

| 구분 | 수 |
|---|---|
| CSV 후보 기업 (헤더 제외) | 38 |
| `status=unverified`로 제외 (URL 없음) | 6 |
| 기존 5개 기업과 동일 기업이라 스킵 | 5 |
| **DB에 신규 추가된 기업** | **27** |
| 그중 크롤링(1단계) + LLM 파싱(2단계) + 위험도 산출(3단계)까지 **전 구간 성공** | **18** |
| robots.txt 차단으로 크롤링 자체를 실행하지 않음 | 5 |
| 크롤링 시도했으나 실패 | 4 |

DB는 로컬 MySQL(`consentradar`)에 실제로 반영했다(company 테이블 5행 → 32행, 재실행해도 중복 insert 없음을 확인). 크롤링·LLM 파싱·위험도 산출은 실제 프로젝트 클래스(`PolicyBodyCrawler`, `LlmClient`, `LlmPromptTemplate`, `LlmRetryModule`, `LlmResponseParser`, `RiskCalculator`)를 그대로 사용한 독립 실행 하네스로 검증했다(OpenAI API 키 사용, `gpt-4o-mini`, temperature=0). **주의: 이번 검증은 크롤링·LLM 분석 결과가 실제로 정상 동작하는지 확인하는 용도이며, `policy_snapshot`/`consent_item`/`risk_score` 테이블에는 아무것도 적재하지 않았다.** 실제 서비스 데이터로 반영하려면 기업별로 `POST /admin/crawl/{companyId}`를 호출해야 한다(1-9 관리자 콘솔 워크플로).

## 2. 기업별 크롤링 결과

### 2-1. 성공 (18개)

| 기업 | 방식 | 수집 길이 | 비고 |
|---|---|---|---|
| 네이버페이 | Jsoup | 17,700자 | |
| 인스타그램 | 헤드리스 폴백 | 48,773자 | Jsoup 0자 → SPA로 판단, Playwright 렌더링 성공 |
| 11번가 | Jsoup | 48,380자 | |
| G마켓 | Jsoup | 33,610자 | |
| 옥션 | Jsoup | 29,636자 | |
| SSG닷컴 | Jsoup | 22,455자 | |
| 카카오페이 | Jsoup | 46,531자 | |
| 카카오뱅크 | 헤드리스 폴백 | 13,403자 | Jsoup 7자(사실상 빈 값) → 헤드리스로 복구 |
| 신한금융그룹 | Jsoup | **691자** | ⚠️ 성공 처리되긴 했으나 다른 기업 대비 지나치게 짧음 — 그룹 공통 정책 페이지가 안내/리다이렉트성 페이지일 가능성. 실제 전문 URL 재확인 권장 |
| 요기요 | Jsoup | 12,209자 | 버전형 URL(`p/20241106.html`)이지만 현재는 정상 수집됨 |
| 쿠팡 | Jsoup | 27,316자 | |
| 카카오T | 헤드리스 폴백 | 18,184자 | |
| 유튜브 | Jsoup | 29,284자 | |
| 멜론 | 헤드리스 폴백 | **502자** | ⚠️ 헤드리스로도 매우 짧은 텍스트만 수집됨 — 실제 약관 본문이 아니라 안내문 일부만 잡혔을 가능성이 높음. 아래 위험도 점수(항목 1개)는 신뢰도가 낮음 |
| 디시인사이드 | Jsoup | 11,105자 | |
| 야놀자(NOL) | 헤드리스 폴백 | 40,026자 | |
| 지그재그 | Jsoup | 8,615자 | 2021년 버전형 URL(`privacy-20210118`)이지만 현재는 정상 수집됨 — 여전히 구버전일 위험은 있음 |
| 알리익스프레스 | Jsoup | 27,362자 | 버전형 URL이지만 현재는 정상 수집됨 |

### 2-2. robots.txt 차단 — 크롤링 미실행 (5개)

스펙 문서 "3-1. robots.txt 차단 사이트 존재" 이슈에 해당. 크롤링 전 robots.txt를 조회해 `Disallow`에 걸리면 **실제 요청 자체를 보내지 않고 스킵**했다(사이트 정책 존중).

| 기업 | URL | robots.txt 규칙 |
|---|---|---|
| 페이스북 | facebook.com/privacy/policy | `Disallow: /` |
| 무신사 | musinsa.com/.../privacy-policy | `Disallow: /` (경로 매칭) |
| 페이코 | id.payco.com/privacyPolicy.nhn | `Disallow: /` |
| 쏘카 | page.modu.kr/policy/privacy | `Disallow: /` |
| 넷플릭스 | netflix.com/privacy | `Disallow: /` |

→ 현재 `PolicyBodyCrawler`에는 robots.txt 검사 로직이 없다(단순 Jsoup GET). 스펙 문서 권고대로 `robots_allowed` 플래그를 `Company`에 추가하거나, 크롤러 진입 전 별도 정책 점검 스텝을 두는 것을 권장한다 — 이번 PR 범위에는 포함하지 않았다(별도 논의 필요).

### 2-3. 크롤링 시도했으나 실패 (4개)

| 기업 | URL | 실패 유형 | 상세 |
|---|---|---|---|
| 라인 | line.me/ko/terms/policy | 봇 차단 추정 | robots.txt는 허용이지만 실제 요청은 `HTTP 403`(3회 재시도 모두 실패). `www.line.me`로 리다이렉트 후 403 — User-Agent 기반 차단으로 추정 |
| 오늘의집 | ohou.se/privacy | 봇 차단 추정 | robots.txt 자체가 403으로 응답(정책 파일도 못 읽음), 본문 요청도 3회 모두 403. 스펙 문서에 기재된 "robots.txt 차단" 사례와 일치 |
| 마켓컬리 | kurly.com/user-terms/privacy-policy | SPA, 렌더링 타임아웃 | Jsoup 0자 → 헤드리스 폴백 시도했으나 `networkidle` 대기 20초 초과(Timeout). 트래킹 스크립트가 많아 네트워크가 계속 활성 상태로 유지되는 것으로 추정 |
| 블라인드 | teamblind.com/kr/privacy | URL 깨짐(404) | 스펙 문서에도 "일반 URL, 재확인 필요"(needs_review)로 표시되어 있었는데 실제로 404 확인됨. 정확한 개인정보처리방침 경로 재조사 필요 |

## 3. 위험도 산출 결과 (LLM 실제 호출, 18개 기업)

Risk Score = DS + (ES × TF × PC × AI) × 2, 등급 5단계: 매우 안전(3.0~7.0) / 안전(7.0~14.0) / 보통(14.0~24.0) / 위험(24.0~36.0) / 매우 위험(36.0~45.5).

| 기업 | 동의 항목 수 | 대표 위험도 점수 | 등급 |
|---|---|---|---|
| 유튜브 | 3 | **43.5** | 매우 위험 |
| 야놀자(NOL) | 5 | **43.5** | 매우 위험 |
| 11번가 | 5 | 32.0 | 위험 |
| 인스타그램 | 3 | 32.0 | 위험 |
| 카카오페이 | 5 | 32.0 | 위험 |
| 지그재그 | 5 | 30.0 | 위험 |
| 신한금융그룹 ⚠️ | 2 | 30.0 | 위험 |
| 네이버페이 | 3 | 28.0 | 위험 |
| G마켓 | 5 | 23.0 | 보통 |
| 카카오뱅크 | 2 | 23.0 | 보통 |
| 요기요 | 5 | 23.0 | 보통 |
| 카카오T | 5 | 23.0 | 보통 |
| 알리익스프레스 | 5 | 23.0 | 보통 |
| 옥션 | 5 | 21.0 | 보통 |
| SSG닷컴 | 4 | 21.0 | 보통 |
| 쿠팡 | 3 | 21.0 | 보통 |
| 디시인사이드 | 4 | 21.0 | 보통 |
| 멜론 ⚠️ | 1 | 15.0 | 보통 |

⚠️ 표시된 신한금융그룹·멜론은 수집된 원문 길이가 비정상적으로 짧아(691자, 502자) 실제 전체 약관이 아니라 일부(안내문/리다이렉트 페이지)만 분석됐을 가능성이 있다. 두 기업 모두 **정확한 전문 URL 재확인 후 재크롤링을 권장**하며, 현재 점수는 참고용으로만 사용해야 한다.

동의 항목별 상세(itemName/ds/es/tf/pc/ai/판단근거/점수, 71건)는 세션 로그의 원자료를 참고하거나 필요 시 재현 가능 — 이 보고서에는 대표 점수만 요약했다.

## 4. LLM 파싱 관련 참고사항

- `LlmClient`는 `llm.enabled=false`이거나 `OPENAI_API_KEY`가 없으면 **입력과 무관하게 항상 동일한 목업 JSON**(카카오 예시 2개 항목)을 반환한다(`LlmClient.mockResponse()`). 이번 검증은 사용자가 제공한 OpenAI API 키로 `llm.enabled=true`, `gpt-4o-mini`, `temperature=0` 설정을 사용해 **실제 LLM 호출**로 진행했다 — 위 3장의 점수는 목업이 아닌 실제 분석 결과다.
- 운영 환경(또는 다른 팀원 로컬)에서 이 파이프라인을 다시 돌리려면 `OPENAI_API_KEY`/`LLM_ENABLED=true` 환경변수가 반드시 설정되어 있어야 한다. 설정하지 않으면 스케줄러/관리자 수동 크롤링 모두 카카오 목업 데이터로 위험도가 계산되므로 실제 서비스에서는 반드시 확인이 필요하다.
- 이번 검증에 사용된 API 키는 세션 환경변수로만 사용했고 코드/커밋/설정 파일 어디에도 저장하지 않았다. **키를 대화창에 평문으로 공유하셨으므로, 이후 로그 노출 가능성을 고려해 OpenAI 대시보드에서 이 키를 재발급(rotate)하시는 것을 권장한다.**

## 5. 미확인(unverified) 기업 — 이번 PR 범위 밖, 별도 TODO

CSV의 `status=unverified` 6개 기업은 privacy_url이 없어 이번 작업에서 완전히 제외했다. 관리자 콘솔(`POST /admin/companies`)에서 URL을 직접 확인해 등록 후 `POST /admin/crawl/{companyId}`로 테스트 크롤링하는 워크플로를 개별 적용해야 한다.

| 기업 | package_name(추정) | 확인 필요 |
|---|---|---|
| 티빙 | com.cjenm.tving | tving.com 하단 개인정보처리방침 링크 |
| 웨이브 | kr.co.wavve | wavve.com 하단 링크 |
| 지니뮤직 | com.dreamus.company | genie.co.kr 하단 링크 |
| 여기어때 | com.gcompany.yeogi(추정) | yeogi.com 하단 링크 |
| 에브리타임 | kr.ac.everytime(추정) | everytime.kr 하단 링크 |
| 우티(UT) | - | 검색 실패, 앱 내 직접 확인 필요 |

추가로, 이번에 크롤링 실패/스킵된 9개 기업(라인·오늘의집·마켓컬리·블라인드·페이스북·무신사·페이코·쏘카·넷플릭스)도 근본 해결(robots 정책 준수 방식의 재설계, 헤드리스 렌더링 대기시간 조정, URL 재조사)이 필요한 후속 작업으로 남긴다.

## 6. 데이터 출처 관련 주의사항

`category`/`legal_name`은 `Company` 엔티티가 NOT NULL로 요구하지만 CSV/스펙 문서에는 없는 컬럼이라, 공개된 사업자 정보를 기준으로 이번에 새로 조사해 채운 값이다(V6 마이그레이션 때와 동일한 성격의 플레이스홀더 — 실제 값은 팀 최종 확인 필요). `isms_certified`도 스펙 문서 조사 시점 스냅샷 값을 그대로 사용했다.
