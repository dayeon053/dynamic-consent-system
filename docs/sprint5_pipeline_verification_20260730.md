# 스프린트5 — 파이프라인 2차 통합 검증 (2026-07-30)

## 1. 검증 목적

5개 기업(카카오, 네이버, 배달의민족, 토스, 당근마켓) 기준으로

```
크롤링 → LLM 파싱 → 변수 매핑 → 위험도 산출 → DB 적재
```

전 과정이 1회 오류 없이 통과하는지 확인한다.

## 2. 사전 조건

- **`LLM_ENABLED=false`** (환경변수 미설정 → `application.yml`의 기본값, `LlmClient.java:31`)
- 따라서 이번 검증은 **전부 mock JSON 기반**이다. 실제 OpenAI API 호출은 한 번도 발생하지 않았다.
- 대상 기업 5개는 이미 DB에 등록되어 있었다: `company_id` 1=카카오, 2=네이버, 3=배달의민족, 4=토스, 5=당근마켓.

## 3. 진행 방식

### 1차 시도 (2026-07-30 낮, 최초 검증)

`POST /admin/crawl/{companyId}` (1~5)를 순차 호출. 5개 기업 모두 크롤링 자체는 성공했으나, 응답이 전부 `"changed":false,"riskAnalysisTriggered":false`로 나왔다.

원인을 코드로 추적한 결과, `PolicyCrawlScheduler.processCompany()`(`scheduler/PolicyCrawlScheduler.java:116-119`)는 `isFirstCollection || snapshot.isChanged()`일 때만 `RiskPipelineService.analyzeAndSaveRisk()`를 호출한다. 5개 기업 모두 2026-07-26에 이미 스냅샷이 존재했고(`isFirstCollection=false`), 오늘 재크롤링한 원문이 이전 스냅샷과 SHA-256 해시가 동일해 "변경 없음"으로 판정됐다(`PolicyChangeDetectionService.java:47`).

**결과: 크롤링 단계만 실행됨. LLM 파싱 → 변수 매핑 → 위험도 산출 → DB 신규 적재는 5개 기업 전부 미실행.** DB 조회로도 확인: `risk_score.scored_at`이 전부 `2026-07-26`(4일 전)이고, `policy_snapshot`/`consent_item`/`risk_score` 행 수가 실행 전후 동일했다. 이는 버그가 아니라 설계된 비용 절감 스킵 로직이 정상 동작한 결과이지만, "1회 오류 없이 전 과정 통과"라는 검증 목표 자체는 충족되지 않았다.

### 2차 시도 (강제 재실행)

전 과정을 실제로 통과시키기 위해 사용자 승인 하에 아래 순서로 진행했다.

1. **백업**: `policy_snapshot`, `consent_item`, `risk_score`의 `company_id IN (1,2,3,4,5)` 행을 `mysqldump`로 백업
   (`--complete-insert --skip-extended-insert`, 행 수 검증까지 완료 — 10건/10건/15건 일치 확인)
2. **삭제**: 같은 조건으로 3개 테이블에서 삭제 (트랜잭션으로 실행, `company` 테이블 자체는 미변경) → `isFirstCollection=true` 상태로 전환
3. **재실행**: `POST /admin/crawl/{companyId}` 1~5 순차 재호출

## 4. 최종 결과표 (2차 시도)

| 기업 | 크롤링 | LLM 파싱(mock) | 변수매핑(upsert) | 위험도산출 | DB 신규 적재 | 결과 |
|---|---|---|---|---|---|---|
| 카카오 | ✅ Jsoup 직접 성공 | ✅ 실행+저장 | ✅ ConsentItem 2건 insert | ✅ RiskScore 3건 (항목2+대표1) | snapshot #67, item #267-268, score #377-379 | ✅ PASS |
| 네이버 | ✅ Jsoup 직접 성공 | ✅ 실행+저장 | ✅ ConsentItem 2건 insert | ✅ RiskScore 3건 | snapshot #68, item #269-270, score #380-382 | ✅ PASS |
| 배달의민족 | ✅ Jsoup 부족(0자) → Playwright 폴백 성공(4444자) | ✅ 실행+저장 | ✅ ConsentItem 2건 insert | ✅ RiskScore 3건 | snapshot #69, item #271-272, score #383-385 | ✅ PASS |
| 토스 | ✅ Jsoup 부족(0자) → Playwright 폴백 성공(13420자) | ✅ 실행+저장 | ✅ ConsentItem 2건 insert | ✅ RiskScore 3건 | snapshot #70, item #273-274, score #386-388 | ✅ PASS |
| 당근마켓 | ✅ Jsoup 직접 성공 | ✅ 실행+저장 | ✅ ConsentItem 2건 insert | ✅ RiskScore 3건 | snapshot #71, item #275-276, score #389-391 | ✅ PASS |

배달의민족·토스는 하이브리드 크롤러의 SPA 폴백(Jsoup → Playwright Chromium)이 설계대로 정상 작동했다.

## 5. 예외/에러

**0건.** `bootrun.log`의 검증 구간(2026-07-30T20:13:51.267 ~ 20:14:21.559)을 `ERROR|Exception|Traceback|FAILED`로 검색한 결과 아무 것도 매칭되지 않았다. `[Pipeline] 1~6단계` 로그가 5개 기업 각각에서 순서대로 출력됐다(LLM 프롬프트 생성 완료 → LLM 호출 시작 → LLM 파싱 성공(동의항목 2개) → 항목별 RiskScore 저장 완료(2건) → 기업 대표 위험도 산출 → 완료(RiskScore 총 3건)).

원본 로그 발췌본: [`docs/logs/pipeline_verification_20260730.log`](logs/pipeline_verification_20260730.log)

> **로그 캡처 관련 알려진 제약**: 이 로그는 PowerShell의 `*>` 리다이렉션으로 캡처됐고, 그 과정에서 한글 로그 메시지 일부가 인코딩 손상(CP949/UTF-8 이중 변환, 복구 불가)을 입었다. 타임스탬프·URL·클래스명·SQL·점수/등급 등 영문·숫자 정보는 전부 정상이며, 손상된 한글 메시지의 원문은 로그 파일 상단 주석에 소스 코드 기준으로 병기해 두었다. DB에 저장된 실제 데이터(한글 항목명 등)는 이 캡처 손상과 무관하며, `--default-character-set=utf8mb4`로 재조회해 정상 저장을 별도로 확인했다(6번 참고).

## 6. DB 최종 검증 수치

`--default-character-set=utf8mb4`로 재조회하여 한글 데이터 정상 저장을 확인했다.

- `policy_snapshot`: **5건** 신규 insert (기업당 1건)
- `consent_item`: **10건** 신규 insert (기업당 2건 — "서비스 이용을 위한 필수 개인정보 수집"(REQUIRED, ds=5), "마케팅 정보 수신 동의"(OPTIONAL, es=3))
- `risk_score`: **15건** 신규 insert (기업당 3건 = 항목별 2건 + 대표 1건), `scored_at`/`created_at` 전부 `2026-07-30`
- 항목별 점수: "서비스 이용을 위한 필수 개인정보 수집" → 17.0점(MEDIUM), "마케팅 정보 수신 동의" → 43.5점(VERY_HIGH), 기업 대표 점수 → 43.5점(VERY_HIGH, 항목 중 최댓값)

## 7. 알려진 한계

- `LLM_ENABLED=false` 상태이므로 `LlmClient.mockResponse()`(`crawler/LlmClient.java:90-126`)의 **고정된 mock JSON**(companyName 필드는 항상 "카카오", 동의항목 2개 고정)이 5개 기업 전부에 동일하게 적용됐다. 그 결과 5개 기업의 ConsentItem/RiskScore 내용이 전부 동일하며, 실제 약관 차이는 반영되지 않는다.
- `RiskPipelineService.analyzeAndSaveRisk()`는 파싱된 `companyName` 필드를 사용하지 않고 인자로 받은 실제 `Company` 엔티티에 저장하므로, mock의 고정 companyName이 다른 기업 데이터를 오염시키는 문제는 없음을 확인했다.
- 실제 LLM(OpenAI) 연동 및 기업별 실데이터 기반 재검증은 **8/20 이후** 예정.

## 8. 데이터 처리 결정

- 원래 실데이터(2026-07-26 기준, 카카오/네이버/배달의민족/토스/당근마켓 각 2개 스냅샷 등)는 **`mysqldump` 백업 파일**로 보관 중이다. (백업 방식은 별도 DB 백업 테이블이 아니라 SQL 덤프 파일이며, 문서 작성 과정에서 사용자에게도 이 점을 재확인함.)
  - 백업 파일: `C:\Users\happy\AppData\Local\Temp\claude\C--consentradar\541b11b7-7e17-4ad0-bce1-788a720f1463\scratchpad\db_backup\pipeline_verify_backup_20260730.sql`
- 이번 강제 재실행으로 생성된 mock 기반 검증 데이터(5개 기업, 4번 결과표)는 **복구하지 않고 그대로 유지**하기로 결정함 (2026-07-30, 사용자 확정).

## 9. 스프린트5 범위 결정 — 알림 흐름

> 이 절은 파이프라인 검증과 무관한 별도 결정사항이며, `docs/planning/개발_계획.md`(로컬 미존재, Notion 원본 사본)를 대신해 이 문서에 기록한다.

**[결정] 알림 흐름 범위**: 감시 포그라운드 상시 알림(`AppLaunchMonitorService`, 구현완료)만 이번 스프린트 범위. 약관변경 실제 푸시(FCM)는 이번 범위 제외, 추후 구현 예정.

**사유**: 신규 개발 규모 큼, 남은 기간 리스크.

## 10-D. risk-history (위험도 추이 그래프)

> ⚠️ 참고: `10-D` 항목이 있다고 알려진 원본 문서(`docs/planning/개발_계획.md` 등)가 이 클론에는 없어(Notion 사본, git 미추적) 기존 텍스트를 직접 편집하지 못하고 이 문서에 새로 옮겨 적었다. "데이터 신선도 표시" 관련 내용은 섞지 않고 risk-history 본연의 내용만 남긴다.

- **[미결정]** 위험도 추이 그래프의 표시 범위(기간 단위 — 예: 최근 7일/30일, 혹은 전체 이력)는 아직 확정되지 않음.
- 관련 API: `GET /users/{userId}/companies/{companyId}/risk-history` (2-4). 저장 로직(`PersonalRiskHistoryService.saveIfAbsent()`)이 실제 호출부에 연결되지 않아(항목 8, 2026-07-30 확인) 현재는 이 엔드포인트를 호출해도 항상 빈 배열이 반환된다 — 그래프 범위 결정 자체보다 이 연결이 먼저 선행되어야 함.

## 10-E. 데이터 신선도 표시

- **[확정]** "확인 시각"(`crawledAt`) 표시는 **공지사항 탭(`/notices`)에만 적용**한다. 위험기관리스트/기업상세는 표시 범위에서 **제외 확정**.
- 위험기관리스트(`CompanyRiskResponse`, 2-1)/기업상세(`ConsentItemResponse`, 2-2)에는 시각 필드를 추가하지 않는다 (기존 요청 취소).
- 위험도 점수/등급은 `personalRiskCalculator.calculate()`가 매 요청 실시간 재계산하므로 애초에 "신선도" 논의와 무관하다 (참고용 기록 — 캐시된 값이 아니라 항상 그 순간 최신 계산 결과이기 때문).
- `crawledAt`은 "변경 시각"이 아니라 "확인 시각"이다(변경이 없어도 매일 갱신됨, 항목 7에서 실측 확인). 문구는 "마지막 업데이트"가 아니라 **"마지막 확인"**으로 표기한다 (예: "마지막 확인: 2026-07-30 03:00").
- **남은 결정 필요 항목 (이재은님 담당)**:
  - 타임존 처리 방식 → 항목 7에서 백엔드 확인 완료: `crawledAt`은 UTC가 아니라 **KST 그대로** 저장·반환되므로, 프론트는 별도 변환 없이 그대로 표시하면 됨 (`api_spec.md` 2-5 참고).
  - 배치 실패 시 표시 정책 (며칠 경과부터 경고 표시할지) — 미정
  - 공지사항 탭 새로고침 동작 문구 — 미정
