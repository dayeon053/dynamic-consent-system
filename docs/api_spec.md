# API 명세서

> 원본: Sprint 01(2026-06-29) 작성 "DB 스키마 & API 명세서 초안"
> 이 문서: Sprint 03(2026-07-21) 기준 실제 구현 코드 대조 후 갱신. 구조(2-1~2-6, 표 형식)는 원안을 그대로 유지하고, 내용만 실제 코드 기준으로 교체/보강했다. 2-7~2-8은 원안에 없던 신규 엔드포인트.
> 2026-07-30 업데이트: 백엔드 확인사항 8건을 코드(파일:라인 단위)와 실 DB 조회, 로컬 서버(localhost:8080) 실제 호출로 전수 검증하고 그 결과를 반영. 대조 대상 코드 추가: `.../consenthistory/UserConsentHistoryController.java`, `.../consenthistory/UserConsentHistoryService.java`, `.../riskhistory/PersonalRiskHistoryService.java`, `.../crawler/PolicyChangeDetectionService.java`, `.../repository/PolicySnapshotRepository.java`, `frontend/app/src/main/java/com/dynamicconsent/data/remote/CompanyMapper.kt`, `frontend/app/src/main/java/com/dynamicconsent/monitor/WatchedAppRegistry.kt`.
> v5, 2026-08-26: 소프트 삭제 도입 및 개발 마감 정리(A-0~A-5 최종 점검). `POST /admin/crawl/{companyId}`에 `force` 강제 재분석 파라미터(2-6) 추가, `GET /companies/{companyId}/consent-items`가 `active=true` 항목만 반환하도록 변경(2-2) — 둘 다 재크롤링해도 예전 ConsentItem이 위험도 계산에 섞이지 않게 하는 소프트 삭제(`ConsentItem.active`, V9 마이그레이션) 도입에 따른 변경. 이번이 이 문서의 마지막 갱신.

---

## 공통 사항

| 항목 | 원안 | 실제 상태 |
|---|---|---|
| Base URL | `https://api.privacyguard.com/v1` | **미적용 (PoC 단계)**. 실제 배포 도메인 없음. 로컬 개발 서버는 `http://localhost:8080` (별도 context-path 설정 없음, 위 URL들이 곧 실제 경로) |
| 인증 방식 | Bearer Token (Authorization 헤더) | 일반 API(`companies`, `consents`, `risk-history` 등)는 여전히 인증 없음(PoC). 단, `/admin/**`는 Sprint 04(PR #27, `SecurityConfig.java`)에서 HTTP Basic + `ROLE_ADMIN` 인증이 추가됨 (2026-07-26) |
| 공통 응답 형식 | `{ "status": 200, "data": {...}, "message": "success" }` | **미적용**. 전부 `ResponseEntity<T>` 또는 `List<T>`를 그대로 반환하며 래핑 없이 응답 바디가 곧 `data`에 해당하는 내용이다. 예: `GET /companies`는 `{status:200,data:[...]}`가 아니라 `[...]` 배열을 바로 반환 |
| 테스트/데모 사용자 | (원안 없음) | ⚠️ **2026-07-30 기준 `users` 테이블이 완전히 비어 있음(0 rows)**. `sql/migration/V1~V5`는 전부 DDL(스키마 변경)만 있고 사용자 INSERT 시드가 없다(`resources/` 하위에 별도 `data.sql`류 시드 파일도 없음). `userId`를 실제로 검증하는 건 PATCH(2-3)뿐이라 GET 계열(2-1, 2-2)은 존재하지 않는 userId로도 200 정상 응답한다 — 각 절의 "실제 검증 결과" 참고 |

---

## 2-1. 기업 목록 조회 (위험도 순 정렬)

- **Method**: GET
- **URL**: `/companies`
- **설명**: 원안과 동일한 취지 — 사용자 맞춤(개인 맞춤) 위험도 높은 순으로 기업 목록 반환. 실제로는 `userId`가 체크한 선택동의 + 필수동의 기준으로 매 요청마다 서버가 실시간 재계산한다(저장된 배치 점수를 그대로 노출하지 않음).
- **Query Parameters**:

  | 이름 | 타입 | 필수 | 기본값 | 설명 |
  |---|---|---|---|---|
  | `userId` | Long | 필수 | - | 사용자 ID (원안과 동일하게 필수). ⚠️ **존재 검증 안 함**: `ConsentApiService.getCompaniesSortedByRisk()`(`ConsentApiService.java:143`)는 `userId`로 사용자를 조회하지 않고 그대로 위험도 계산에만 사용하므로, DB에 없는 userId를 넣어도 200과 함께 정상 목록이 반환된다(2026-07-30 로컬 서버 실 호출로 확인, `users` 테이블이 비어 있는 상태에서도 200) |
  | `sort` | String | 선택 | `risk_score_desc` | sort 파라미터는 현재 미사용 상태입니다. 서버는 위험도(risk_score) 내림차순 정렬만 지원하며, sort 값은 컨트롤러에서 수신되지만 서비스 로직에는 반영되지 않습니다. 향후 정렬 옵션 확장 필요 시 재검토 예정입니다. |

- **응답**: `List<CompanyRiskResponse>` (배열, 래핑 없음)
- **응답 예시** (실제 필드명 camelCase, 실제 등급 enum 값 기준):
  ```json
  [
    {
      "companyId": 1,
      "companyName": "카카오",
      "legalName": "(주)카카오",
      "category": "SNS",
      "packageName": "com.kakao.talk",
      "privacyUrl": "https://www.kakaocorp.com/page/detail/9610",
      "ismsCertified": false,
      "riskScore": 43.5,
      "riskGrade": "VERY_HIGH"
    }
  ]
  ```
  - `legalName`/`category`는 마이그레이션 V6(`sql/migration/V6__add_category_and_legal_name_to_company.sql`)에서 추가된 필드로, 기업상세 '정보' 탭(태스크 4-8)에 쓰인다.
  - `riskGrade`는 원안의 `"DANGEROUS"` 같은 임의 문자열이 아니라 실제 5단계 enum(`VERY_LOW` / `LOW` / `MEDIUM` / `HIGH` / `VERY_HIGH`) 중 하나다.
  - 해당 기업에 동의 항목이 없어 위험도를 계산할 수 없으면 `riskScore`/`riskGrade`는 `null`로 내려간다.
  - ⚠️ **`category`/`logoText`/`logoColor` 필드 자체가 응답에 없다.** `Company` 엔티티(`entity/Company.java:15-58`)와 `CompanyRiskResponse` DTO(`api/dto/CompanyRiskResponse.java`) 어디에도 이 3개 필드가 없음을 코드로 확인함(백엔드 전체 grep 0건). **오해 정정**: "백엔드 CompanyMapper가 하드코딩한다"는 원래 가설과 달리, 백엔드에는 이 이름의 매퍼 자체가 없다 — 값을 하드코딩하는 지점은 **프론트엔드**다. 안드로이드 앱 `frontend/app/src/main/java/com/dynamicconsent/data/remote/CompanyMapper.kt:33-37`에서 서버가 안 주는 값을 클라이언트가 임시로 채워 넣는다:
    ```kotlin
    category = "기타",                              // CompanyMapper.kt:33
    logoText = response.companyName.take(1),        // CompanyMapper.kt:36 (기업명 첫 글자)
    logoColor = DEFAULT_LOGO_COLOR,                  // CompanyMapper.kt:37 (26번 줄: 0xFF00752FL 고정값)
    ```
    카테고리/로고를 실제로 제공하려면 백엔드 스키마(`company` 테이블 컬럼 추가)와 API 응답 확장이 먼저 필요하고, 그 전까지 프론트는 계속 이 임시값을 쓴다.
  - `packageName`은 DB 컬럼상 nullable이다(`entity/Company.java:24` — `nullable=false` 미지정, unique만 지정). 2026-07-30 기준 실 DB `company` 5행 전부 non-null이라 현재는 영향 없지만, null인 기업이 생기면 안드로이드 앱의 감시 대상 등록(`frontend/.../monitor/WatchedAppRegistry.kt:88-91`, `mapNotNull { org.packageName?.takeIf { it.isNotBlank() } ... }`)에서 **에러 없이 조용히 스킵**되어 오버레이/감시 대상에서 빠진다.

---

## 2-2. 기업 상세 조회

- **Method**: GET
- **URL**: `/companies/{company_id}`
- **상태**: **미구현 (원안은 통합형이었으나 실제로는 `/companies/{companyId}/consent-items`로 분리 구현됨)**

원안은 기업 기본정보 + 동의 항목 전체 목록 + 위험도 점수를 한 응답에 통합해서 내려주는 단일 엔드포인트였다. 실제 코드에는 이 형태의 통합 엔드포인트가 없고, 대신 아래처럼 **역할이 분리된 두 엔드포인트**로 나뉘어 구현되어 있다.

- 기업 기본정보 + 위험도 → 2-1 (`GET /companies`) 응답의 배열 원소 하나로 대체
- 동의 항목 전체 목록 → 아래 실제 대응 엔드포인트로 대체

### 실제 대응 엔드포인트: `GET /companies/{companyId}/consent-items`

- **Method**: GET
- **URL**: `/companies/{companyId}/consent-items`
- **설명**: 해당 기업의 필수+선택 동의 항목 전체를 5대 변수(DS/ES/TF/PC/AI) 점수와 함께 반환. `userId`가 체크한 선택동의 항목은 `checked: true`로 표시된다.
- ⚠️ **2026-08-26 변경**: `active=true`(소프트 삭제 안 된) 항목만 반환한다. 재크롤링/재분석 결과 더 이상 유효하지 않은 예전 항목(`ConsentItemUpsertService.deactivateMissing()`이 `active=false`로 전환한 것)은 이 목록에서 제외된다 — 사용자가 더 이상 존재하지 않는 동의 항목을 화면에서 보는 일을 막기 위함. 해당 항목의 `UserConsentCheck`/`UserConsentHistory` 이력 자체는 DB에 그대로 보존된다(하드 삭제 아님).
- **Path Parameters**: `companyId` (Long, 필수)
- **Query Parameters**: `userId` (Long, 필수) — 원안엔 없던 파라미터. 사용자별 체크 상태(`checked`)를 판단하려면 실제 구현상 필수다. ⚠️ 이 엔드포인트도 2-1과 동일하게 **userId 존재 여부를 검증하지 않는다**(`ConsentApiService.getConsentItems()` → `personalRiskCalculator.findCheckedOptionalItemIds(userId, companyId)`가 단순 조회일 뿐 사용자 조회/검증이 없음). 2026-07-30 로컬 서버 실 호출로 `users` 테이블이 빈 상태에서 존재하지 않는 userId=1로도 200 정상 응답 확인.
- **응답**: `List<ConsentItemResponse>`
- **응답 예시**:
  ```json
  [
    {
      "consentItemId": 10,
      "itemName": "서비스 이용을 위한 필수 개인정보 수집",
      "itemType": "REQUIRED",
      "checked": true,
      "dsScore": 5,
      "esScore": 2,
      "tfScore": 3,
      "pcScore": 1.0,
      "aiScore": 1.0
    },
    {
      "consentItemId": 11,
      "itemName": "마케팅 정보 수신 동의",
      "itemType": "OPTIONAL",
      "checked": false,
      "dsScore": 3,
      "esScore": 3,
      "tfScore": 3,
      "pcScore": 1.5,
      "aiScore": 1.5
    }
  ]
  ```
  - `itemType`은 `REQUIRED`(필수) 또는 `OPTIONAL`(선택) 중 하나. `REQUIRED` 항목은 `checked`가 항상 `true`로 내려간다.
  - 기업 기본정보(이름/URL 등)나 종합 위험도는 이 응답에 없다 — 필요하면 2-1 응답과 클라이언트에서 조합해야 한다.

---

## 2-3. 동의 항목 체크/해제

- **Method**: PATCH — 원안과 일치
- **URL**: `/users/{userId}/consents/{consentItemId}` (원안의 `user_id`/`consent_item_id`가 실제로는 camelCase `userId`/`consentItemId`)
- **설명**: 원안과 취지는 동일(선택동의 토글 후 위험도 즉시 재산출) — 단, 아래 두 가지가 원안과 다르다.
  1. **Request Body 없음**: 원안은 `is_checked`(Boolean)를 body로 받아 "원하는 상태로 설정"하는 방식이었지만, 실제 구현은 body 없이 **현재 저장된 체크 상태를 그대로 반전(toggle)**시킨다. 즉 같은 요청을 두 번 연속 보내면 체크 → 해제 → 체크로 왕복하며, 멱등(idempotent)하지 않다.
  2. 재산출 기준: 필수동의 전체 + 이 사용자가 실제 체크한 선택동의만으로 계산한다(워스트 케이스 아님).
- **Request Body**: **없음** (원안의 `is_checked` 필드는 실제로 존재하지 않음)
- **응답**: `ConsentPatchResponse`
- **응답 예시**:
  ```json
  {
    "consentItemId": 11,
    "checked": true,
    "newRiskScore": 72.1,
    "newRiskGrade": "MEDIUM"
  }
  ```
  - `consentItemId`, `checked`는 원안에 없던 필드로 실제 응답엔 포함되어 있다.
  - 원안의 `changed_at`(변경 시각)은 응답 필드에 **없다** — 엔티티(`UserConsentCheck.changedAt`)에는 저장되지만 API 응답으로는 내려주지 않는다.
  - `newRiskGrade`는 2-1과 동일하게 실제 5단계 enum 값(`VERY_LOW`~`VERY_HIGH`) 기준.
- **에러 케이스 — 존재하지 않는 userId**: 2-1/2-2와 달리 이 엔드포인트는 `ConsentApiService.toggleConsent()`(`ConsentApiService.java:85-86`)에서 `userRepository.findById(userId).orElseThrow(IllegalArgumentException)`으로 사용자 존재를 검증한다. 이 예외를 처리하는 `@ExceptionHandler`/`@ControllerAdvice`가 프로젝트에 없어(전체 grep 0건) Spring 기본 처리로 **HTTP 500**이 된다(400이 아님). 2026-07-30 로컬 서버에 존재하지 않는 userId=1로 실제 호출해 500 확인함(`{"status":500,"error":"Internal Server Error",...}`). 이 실패는 상태 변경(`check.setChecked`/`save`) 이전에 발생하므로 DB에는 아무 영향이 없다(호출 후 `user_consent_check`/`risk_score` 테이블 재조회로 부작용 없음 확인).

---

## 2-4. 위험도 이력 조회

- **Method**: GET — 원안과 일치
- **URL**: `/users/{userId}/companies/{companyId}/risk-history` (camelCase 반영, 그 외 경로 구조는 원안과 동일)
- **설명**: 원안과 취지 동일(날짜별 위험도 점수 이력, 추이 그래프용). 실제로는 "이 사용자의 개인 맞춤 대표 위험도"(`isRepresentative=true`, `user_id` NOT NULL) 이력만 반환한다.
- **Query Parameters**: **없음** — 원안의 `from`/`to`(기간 필터) 파라미터는 실제로 구현되어 있지 않다. 현재는 저장된 이력을 필터 없이 날짜 오름차순으로 전부 반환한다.
- **응답**: `List<RiskScoreHistoryItemDto>`
- **응답 예시**:
  ```json
  [
    {
      "scoredAt": "2026-06-29",
      "totalScore": 87.4,
      "grade": "VERY_HIGH"
    },
    {
      "scoredAt": "2026-06-30",
      "totalScore": 72.1,
      "grade": "MEDIUM"
    }
  ]
  ```
  - 필드명은 원안과 거의 동일(`total_score`→`totalScore`, `scored_at`→`scoredAt`)하되 camelCase.
- **저장 경로 (2026-08-08 배치 연결 완료)**: 이력을 쌓는 저장 로직은 두 곳에서 호출된다.
  - **배치**: `PolicyCrawlProcessor.processCompany()`가 정책 변경 여부(shouldAnalyze)와 무관하게 매일 밤 무조건, 해당 기업에 `UserConsentCheck` row가 있는(=이 기업을 실제로 접한) 사용자 전원에 대해 `PersonalRiskHistoryService.saveIfAbsent()`를 호출한다. 오늘자 row가 이미 있으면 건너뛴다.
  - **PATCH 토글**: `ConsentApiService.toggleConsent()`가 매 호출마다 `PersonalRiskHistoryService.saveOrUpdateToday()`를 호출한다. 오늘자 row가 있으면 최신 점수로 갱신(같은 날 여러 번 토글해도 오늘자 row는 1건 유지), 없으면 새로 만든다.
  - 두 경로 모두 조회 범위를 항상 "오늘 날짜"로 좁혀서 upsert하므로, 과거 날짜 row는 어느 쪽 호출로도 덮어써지지 않는다(append-only). 과거 버그였던 "가장 최근 row를 날짜 무관하게 덮어쓰기"는 `docs/known_issues.md`의 "PATCH 토글이 위험도 히스토리를 append-only로 쌓지 못하던 버그" 항목 참고.
- **알려진 제약**: 배치가 대상으로 삼는 사용자는 "이 기업에 `UserConsentCheck` row가 있는 사용자"로 한정된다 — 한 번도 동의 화면을 연 적 없는 사용자는 배치 대상에서 제외된다(히스토리를 시작할 기준 데이터가 없으므로).

---

## 2-5. 약관 변경 알림 목록 조회

- **상태**: **구현 완료 (2026-07-30)**
- **관련 코드**: `notice/NoticeController.java`, `notice/NoticeResponse.java`, `repository/PolicySnapshotRepository.findAllByOrderByCrawledAtDesc()`
- **Method**: GET
- **URL**: `/notices`
- **설명**: 전체 기업의 약관 스냅샷 전체를 확인 시각(`crawledAt`) 내림차순으로 페이징 반환한다(공지사항 탭). 기존에 이미 쌓여 있던 `PolicySnapshot`/`PolicyChangeDetectionService.detectAndSave()`(`crawler/PolicyChangeDetectionService.java:33-56`) 데이터를 그대로 재사용한다.
- **Query Parameters**: `page`(Int, 선택, 기본값 0), `size`(Int, 선택, 기본값 20) — offset 방식 페이징(`PageRequest.of(page, size)`)
- **응답**: `List<NoticeResponse>` (배열, 래핑 없음)
- **응답 예시** (2026-07-30 로컬 서버 `GET /notices?page=0&size=20` 실제 호출 결과):
  ```json
  [
    { "companyId": 5, "companyName": "당근마켓", "crawledAt": "2026-07-30T20:14:21.473038", "isChanged": false },
    { "companyId": 4, "companyName": "토스", "crawledAt": "2026-07-30T20:14:17.288506", "isChanged": false },
    { "companyId": 3, "companyName": "배달의민족", "crawledAt": "2026-07-30T20:14:09.253028", "isChanged": false },
    { "companyId": 2, "companyName": "네이버", "crawledAt": "2026-07-30T20:13:55.408537", "isChanged": false },
    { "companyId": 1, "companyName": "카카오", "crawledAt": "2026-07-30T20:13:51.296765", "isChanged": false }
  ]
  ```
  - ⚠️ **`crawledAt`은 "변경 시각"이 아니라 "확인 시각"이다.** 약관이 실제로 바뀌지 않아도 매 크롤링(새벽 3시 배치/관리자 수동 트리거)마다 최신 레코드의 `crawledAt`만 갱신된다(`PolicyChangeDetectionService.detectAndSave()` — 해시 동일하면 새 레코드를 만들지 않고 기존 레코드의 `crawledAt`만 `LocalDateTime.now()`로 덮어씀). 실제로 변경이 있었는지는 `isChanged` 필드로 판단해야 한다.
  - **`isChanged` 필드명 관련 실측 확인**: `NoticeResponse`는 record가 아니라 class + `isChanged()` getter로 만들었지만, 그것만으로는 Jackson이 여전히 "is" 접두사를 벗겨 `"changed"`로 직렬화한다(실제로 처음 구현·호출했을 때 `"changed":false`로 나오는 것을 확인함). `@JsonProperty("isChanged")`를 getter에 명시적으로 붙여야 응답 필드명이 정확히 `isChanged`로 고정된다 — 이 프로젝트에서 boolean 필드를 `isXxx`로 노출하려면 record든 class든 별도 조치(record는 컴포넌트명 그대로 직렬화되지만 그 이름 자체가 `isXxx`가 아니라 다른 걸로 오해하기 쉬우니, class는 `@JsonProperty` 없이는 항상 깎인다는 점을 유의).
  - **타임존**: `crawledAt`은 UTC가 아니라 **KST 그대로** 저장·반환된다. `PolicySnapshot.crawledAt`도 `UserConsentHistory.changedAt`(2-8)과 동일하게 `LocalDateTime.now()`로 저장되고, 서버 JVM 기본 타임존이 Asia/Seoul(KST)이라 별도 변환 없이 이미 KST 값이다 — 2-8의 `changedAt`과 일관되게 이 API도 변환 없이 그대로 내려준다.

---

## 2-6. 크롤링 수동 트리거 (관리자)

- **상태**: **구현됨** (`AdminCrawlController.triggerCrawl`, PR #24/#26/#27, `force` 파라미터 추가)
- **Method**: POST — 원안과 일치
- **URL**: `/admin/crawl/{companyId}` (원안의 `company_id`가 실제로는 camelCase `companyId`)
- **Query Parameter**: `force` (boolean, optional, 기본값 `false`)
  - `force=true`면 `shouldAnalyze` 판단(`isFirstCollection || changed`)을 건너뛰고 무조건 `riskAnalysisTriggered=true`로 위험도 재산출을 강제 실행한다.
  - **용도**: 재크롤링 텍스트가 동일해도(`changed=false`) 예전 오염된 `ConsentItem`을 정리해야 하는 관리 목적. 예를 들어 페이지 내용은 그대로인데 그 기업의 `ConsentItem`에 예전(mock 등) 항목이 `active=true`로 남아 위험도 계산에 잘못 섞여 들어가는 경우, 일반 크롤링은 해시가 같아서 재분석 자체가 스킵되므로 절대 정리되지 않는다 — `force=true`로 강제 재분석해야 `ConsentItemUpsertService.deactivateMissing()`이 이번 크롤링 결과에 없는 예전 항목을 소프트 삭제(`active=false`)한다.
  - `/admin/**`이므로 기존 `ROLE_ADMIN` 인증이 동일하게 적용된다(별도 권한 없음).
- **인증**: `ROLE_ADMIN` 필요 (HTTP Basic — 공통 사항 참고). 미인증/오인증 시 401
- **설명**: 특정 기업 1건에 대해 크롤링 → 변경감지 → (최초 수집이거나 실제로 약관이 변경됐거나 `force=true`면) 위험도 재산출까지 동기적으로 즉시 실행한다(`PolicyCrawlScheduler.runForCompany`). 원안의 "비동기 트리거 후 상태만 반환" 방식이 아니라, 요청이 끝날 때까지 크롤링·(필요 시) LLM 호출·DB 저장이 전부 완료된 뒤 결과를 응답한다.
- **응답**: `AdminCrawlTriggerResponse`
- **응답 예시 — 성공 (변경 감지 + 위험도 재산출됨)**:
  ```json
  {
    "companyId": 1,
    "companyName": "카카오",
    "changed": true,
    "riskAnalysisTriggered": true,
    "success": true,
    "message": "triggered"
  }
  ```
  - `changed`: 이번 크롤링에서 전일 대비 약관 변경이 감지됐는지 (텍스트 실제 변경 여부 그대로 — `force=true`로 강제 재분석됐어도 텍스트가 안 바뀌었으면 `changed`는 여전히 `false`다)
  - `riskAnalysisTriggered`: 최초 수집이거나 `changed=true`이거나 `force=true`라서 LLM 재분석까지 실행했는지 (셋 다 아니면 `false`이고 크롤링 확인만 하고 끝남)
- **응답 예시 — 성공 (`force=true`, 텍스트는 안 바뀌었지만 강제 재분석됨)**:
  ```json
  {
    "companyId": 2,
    "companyName": "네이버",
    "changed": false,
    "riskAnalysisTriggered": true,
    "success": true,
    "message": "triggered"
  }
  ```
- **응답 예시 — 실패 (존재하지 않는 companyId, HTTP 404)**:
  ```json
  {
    "companyId": 999,
    "companyName": null,
    "changed": false,
    "riskAnalysisTriggered": false,
    "success": false,
    "message": "존재하지 않는 companyId: 999"
  }
  ```
  - 그 외 크롤링/LLM 호출 중 예외가 나면 HTTP 500 + 동일한 실패 형식(`success: false`, `message`에 예외 메시지)으로 응답한다.

---

## 2-7. 관리자 기업 등록/삭제

- **상태**: **구현됨** (`AdminController`, PR #27) — 원안에 없던 신규 엔드포인트(1-9 관리자 기업 관리)
- **인증**: `ROLE_ADMIN` 필요 (HTTP Basic — 공통 사항 참고)

### `POST /admin/companies` — 기업 등록

- **Request Body**: `CreateCompanyRequest`
  ```json
  {
    "companyName": "카카오",
    "legalName": "(주)카카오",
    "category": "SNS",
    "packageName": "com.kakao.talk",
    "privacyUrl": "https://www.kakaocorp.com/page/detail/9610",
    "ismsCertified": false
  }
  ```
  - `companyName`/`legalName`/`category`/`packageName`/`privacyUrl`은 필수(비어있으면 400) — `legalName`/`category`는 마이그레이션 V6에서 추가됨
- **응답**: `CompanyResponse` (HTTP 201)
  ```json
  {
    "companyId": 6,
    "companyName": "카카오",
    "legalName": "(주)카카오",
    "category": "SNS",
    "packageName": "com.kakao.talk",
    "privacyUrl": "https://www.kakaocorp.com/page/detail/9610",
    "ismsCertified": false,
    "createdAt": "2026-07-26T10:00:00",
    "updatedAt": "2026-07-26T10:00:00"
  }
  ```
- **에러 케이스**:
  | 상황 | 상태 코드 | 응답 |
  |---|---|---|
  | 필수값 누락/공백 | 400 | `AdminErrorResponse` (`IllegalArgumentException`) |
  | `packageName` 중복(이미 등록된 기업) | 409 | `AdminErrorResponse` (`CompanyConflictException`) |

### `DELETE /admin/companies/{companyId}` — 기업 삭제

- **응답**: 성공 시 본문 없음 (HTTP 204)
- **설명**: `Company`에 `ConsentItem`/`PolicySnapshot`/`RiskScore`가 `cascade=ALL`로 걸려있어 삭제 시 연관 데이터가 통째로 같이 지워질 수 있다. 이를 막기 위해 **연관 데이터가 하나라도 있으면 삭제를 거부**한다.
- **에러 케이스**:
  | 상황 | 상태 코드 | 응답 |
  |---|---|---|
  | 존재하지 않는 companyId | 404 | `AdminErrorResponse` (`IllegalArgumentException`) |
  | 연관된 `ConsentItem`/`PolicySnapshot`/`RiskScore`가 하나라도 있음 | 409 | `AdminErrorResponse` (`CompanyConflictException`) — 연관 데이터를 먼저 정리해야 함 |

### `AdminErrorResponse` 공통 에러 형식
```json
{ "message": "이미 등록된 packageName입니다: com.kakao.talk" }
```

---

## 2-8. 동의 변경 이력 조회

- **상태**: **구현됨, 원안에 없던 신규 엔드포인트 — 2026-07-30 확인 결과 이 문서에 계속 누락되어 있었어서 이번에 추가함**
- **관련 코드**: `consenthistory/UserConsentHistoryController.java`, `consenthistory/UserConsentHistoryService.java`, `consenthistory/UserConsentHistoryItemDto.java`, 테이블 `sql/migration/V5__create_user_consent_history.sql`
- **Method**: GET
- **URL**: `/users/{userId}/consents/history`
- **설명**: 2-3(PATCH 토글)이 발생할 때마다 `UserConsentHistoryRecorder`(`ConsentApiService.java:103`)가 이 이력 테이블에 append-only로 한 건씩 기록한다. `user_consent_check`는 "현재 상태"만 덮어써 이력이 안 남는 반면, 이 API는 변경 시각별 전체 이력을 그대로 보여준다.
- **Path Parameters**: `userId` (Long, 필수)
- **Query Parameters**: 없음
- **응답**: `List<UserConsentHistoryItemDto>` (변경 시각 오름차순, 배열, 래핑 없음)
- **응답 예시**:
  ```json
  [
    {
      "consentItemId": 268,
      "itemName": "마케팅 정보 수신 동의",
      "companyId": 1,
      "companyName": "카카오",
      "isChecked": true,
      "changedAt": "2026-07-29T10:15:00"
    },
    {
      "consentItemId": 268,
      "itemName": "마케팅 정보 수신 동의",
      "companyId": 1,
      "companyName": "카카오",
      "isChecked": false,
      "changedAt": "2026-07-29T14:02:00"
    }
  ]
  ```
  - `consentItemId`/`itemName`/`companyId`/`companyName`은 이력 시점의 `ConsentItem`/`Company`를 조인해 채운 값(스냅샷이 아니라 현재 연관 엔티티 기준).
  - 2-3 응답의 `checked`와 달리 이 응답은 `isChecked`(record 컴포넌트명 그대로 직렬화)다.
  - 이 엔드포인트도 2-1/2-2와 동일하게 **userId 존재 여부를 검증하지 않는다** — 이력이 없거나 존재하지 않는 userId면 빈 배열 `[]`을 200으로 반환한다(`UserConsentHistoryRepository.findByUserIdOrderByChangedAtAsc`가 단순 조회이기 때문).
