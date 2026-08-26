# CHANGELOG

## 2026-08-26 — 개발 마감 최종 점검 (fix/final-audit-a0-a5)

### 배경

PR #49(`fix/final-audit-a0-a5`) 최종 점검 및 리뷰 대응, 그리고 그 과정에서 우연히
발견된 "네이버·배민·토스·당근마켓 위험도가 서로 구분 안 되는" 문제를 근본 원인까지
추적해 고치는 하루짜리 작업. 프론트 중복 코드 정리부터 시작해서, 실제로는 백엔드
데이터 무결성 버그(예전 mock/오염 데이터가 안 지워지고 위험도 계산에 계속 섞여
들어가는 문제)를 찾아 구조적으로 해결하는 데까지 이어졌다.

### 발견된 이슈와 해결

**1. PR #49 ↔ develop(#41) 중복 코드**
프론트 동의이력 연동 파일 5개가 develop에 이미 머지된 #41과 중복되어 컴파일 충돌
위험이 있었다. 해당 파일 제거 후 develop과 merge, 충돌 없음을 `git merge-tree`로
재확인.

**2. A-0~A-5 최종 점검 반영** (커밋 `de41030`, 2026-08-25)
404 처리, `notices` 필터, 테스트 시드 데이터, 동의 이력 연동, `category` 필드 검증.

**3. PATCH body 계약 정정** (커밋 `af3137c`/`8ab7d45`)
동의 토글 PATCH가 반전(toggle) 대신 원하는 상태(`{"checked": true/false}`)를 body로
받도록 바꿔 멱등성을 확보. `docs/api_spec.md` v3에 반영됨.

**4. mock 데이터 오염 발견 → 소프트 삭제 구조 도입 (오늘 핵심 작업)**
- **증상**: `GET /companies?userId=1`에서 companyId 2~5의 위험도가 전부 17.0으로
  동일하게 나옴.
- **1차 원인**: `LlmClient`가 `LLM_ENABLED=false`(기본값)일 때 회사와 무관하게
  항상 같은 고정 mock 응답을 반환 — 실제 LLM 재크롤링으로 해결.
- **2차 원인(더 심각)**: 재크롤링으로 진짜 데이터가 들어와도, `RiskPipelineService`에
  "이번 크롤링 결과에 없는 예전 ConsentItem을 어떻게 할지" 미결 TODO가 있어 예전
  mock 항목이 안 지워지고 새 항목과 함께 위험도 계산에 계속 섞여 들어감(배민/토스/
  당근마켓, 나중엔 네이버도 동일 증상 재발견).
- **해결**: `ConsentItem`에 `is_active` 컬럼(V9 마이그레이션) 추가. 재분석 시 이번
  결과에 없는 기존 항목은 하드 삭제 대신 `active=false`로 소프트 삭제
  (`ConsentItemUpsertService.deactivateMissing()`). `PersonalRiskCalculator`와
  `GET /companies/{id}/consent-items`는 `active=true`만 조회하도록 변경.
  `UserConsentCheck`/`UserConsentHistory` 이력은 그대로 보존.
- **부가 기능**: 해시가 같아 재분석 자체가 스킵되는 경우(정책 텍스트는 안 바뀌었는데
  예전 오염 데이터만 정리해야 하는 경우)를 위해 `POST /admin/crawl/{id}?force=true`
  강제 재분석 옵션 추가.
- **검증**: 실제 운영 크롤링(카카오 `changed:true` 케이스)에서 사라진 항목이
  하드 삭제 없이 `active=0`으로 전환되는 걸 라이브로 확인. 신규 통합 테스트 2건
  (`RiskPipelineServiceNewItemIntegrationTest`)으로 회귀 방지.

**5. 한글 인코딩 리스크 예방 (실제 버그는 아니었음)**
"DB 한글이 깨졌다"는 의심을 받았으나, 바이트 단위(HEX 비교) 검증 결과 **DB 데이터는
처음부터 정상 UTF-8**이었음을 확인 — 그동안 보였던 `???`/mojibake는 `mysql` CLI
latin1 세션, Windows 콘솔 코드페이지 등 순수 클라이언트 표시 문제였다. 다만
`backend/consentradar/build.gradle`에 UTF-8 컴파일 인코딩 강제 설정이 빠져있던 건
사실이라(같은 저장소 `common-model`에는 이미 있었음) 예방 차원에서 반영, 재발
방지용 한글 round-trip 통합 테스트도 추가.

### 오늘 커밋 목록 (fix/final-audit-a0-a5)

1. `feat: ConsentItem 소프트 삭제 구현 (is_active 컬럼, deactivateMissing)`
2. `refactor: 사용하지 않는 ConsentItemBatchService 관련 코드 제거`
3. `feat: /admin/crawl에 force 강제 재분석 옵션 추가`
4. `fix: Gradle 컴파일러 UTF-8 인코딩 설정 추가 (잠재적 인코딩 버그 예방)`
5. `test: 한글 데이터 round-trip 통합 테스트 추가`
6. `docs: 배포 전 체크리스트 신규 작성`

(자정을 넘겨 커밋 타임스탬프는 2026-08-27 새벽으로 찍혀 있으나, 조사·작업은 전부
2026-08-26 하루 동안 이루어졌다.)

### 남아있는 결정사항 / TODO

- **소프트 삭제된 예전 항목의 최종 정리 정책 미정**: 지금은 `active=false`로 무기한
  보존한다. 일정 기간(예: 90일) 지난 비활성 항목을 완전 삭제할지, 계속 보존할지는
  아직 팀 결정 필요.
- **DB 접속 정보(`spring.datasource.*`) 환경변수 오버라이드 없음**: `admin.security.*`,
  `llm.*`와 달리 하드코딩되어 있음 — 배포 전 `${DB_USERNAME:root}` 형태로 바꿔야
  환경변수 주입이 가능하다. `DEPLOYMENT_CHECKLIST.md` 2번 참고.
- **PoC 관리자 인증**: 고정 in-memory 계정 1개(`SecurityConfig`)로 `/admin/**` 전체를
  보호 중. 서비스 규모가 커지면 User 테이블 기반 로그인으로 교체 필요.

### 배포 전 체크리스트

저장소 루트 [`DEPLOYMENT_CHECKLIST.md`](DEPLOYMENT_CHECKLIST.md) 참고 — admin 계정,
DB 접속 정보, LLM(`OPENAI_API_KEY` 등) 연동, 시크릿 유출 확인 항목 정리돼 있음.
