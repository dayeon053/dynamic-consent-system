# API 명세서 (v3 최종본)

> 원본: Sprint 01(2026-06-29) 작성 "DB 스키마 & API 명세서 초안"
> v1: Sprint 03(2026-07-21)~2026-08-08 실제 코드 대조 후 갱신본 (api_spec.md)
> v2(2026-08-25): v1에서 미확정·모순 상태로 남아있던 5가지 쟁점을 팀 최종 결정으로 확정
> **v3(이 문서, 2026-08-25 재수정)**: A-0~A-5 수정 작업 중 v2의 결정 3번(PATCH 토글 방식)이 실제 코드와 다르다는 사실이 발견됨. 실제 백엔드·안드로이드 코드는 이미 body에 `checked` 값을 지정하는 멱등 방식으로 구현·동작 중이었음(v1/v2 판단 근거였던 코드 조사가 낡은 정보였던 것으로 확인). 코드를 되돌리지 않고 문서를 실제 코드에 맞춰 정정한다. 그 외 내용은 v2와 동일.

---

## 🔒 확정된 결정 사항 (2026-08-25 팀 최종 확정)

아래 5개 항목은 이전 버전들에서 미확정·모순·미결정 상태로 남아 있던 것을 이번에 최종 확정한 것이다. 이 문서의 나머지 내용과 충돌하는 부분이 있다면 이 섹션이 우선한다.

| # | 쟁점 | 결정 | 근거 |
|---|---|---|---|
| 1 | `GET /notices`가 변경분만 줄지, 전체를 주고 프론트가 필터링할지 | **서버에서 `isChanged=true`인 것만 필터링해서 반환** (아래 2-5 갱신) | 5-1(변경 감지 시 푸시), 5-2(변경 내역 탭)의 기획 의도와 일치. 매일 변경 없는 기업까지 노출되는 걸 막아야 함 |
| 2 | `category`/`logoText`/`logoColor`를 백엔드가 제공할지 | **지금은 안 함.** 프론트(`CompanyMapper.kt`) 하드코딩 유지, 백엔드 로고 필드 확장은 추후 개선 항목으로 이월. 단 `category`는 이미 DB/entity에 있을 가능성이 높으므로(2-7 참고), 있다면 응답 DTO 매핑만 추가 | 마감이 임박한 상태에서 스키마 변경 대비 실이익이 낮은 화장 기능(로고 표시)이라 우선순위 낮음. category는 저비용 수정이라 있으면 바로 반영 |
| 3 | `PATCH .../consents/{consentItemId}`를 토글로 유지할지, 특정 값 지정 방식으로 바꿀지 | ~~토글 방식 유지~~ **[v3 정정] 이미 body에 `checked`(Boolean)를 지정하는 멱등 방식으로 구현·운영 중임이 확인됨.** 코드는 그대로 두고 문서만 이 사실에 맞춰 정정한다 | v1/v2 판단이 "body 없음"이라는 낡은 코드 조사 결과에 근거했던 것으로 확인(2026-08-25). 실제 코드(백엔드 `ConsentPatchRequest.checked`, 안드로이드 `ApiOrganizationRepository.patchConsent`)는 이미 원안과 유사한 멱등 방식이며, 이 방식이 애초에 우려했던 "중복 탭/재시도 시 반대로 토글" 문제 자체를 구조적으로 없앤다. 이미 300ms 디바운스까지 추가로 구현돼 있어 이중으로 안전함 |
| 4 | `changed_at`을 PATCH 응답에 추가할지 | **추가 안 함.** 2-8 `GET /users/{userId}/consents/history`를 변경 이력의 단일 소스로 확정. 4-7(동의 변경 내역 탭)은 반드시 2-8을 사용 | 이미 구현된 전용 이력 API가 있으므로 PATCH 응답에 중복으로 넣을 이유 없음 |
| 5 | 존재하지 않는 `userId`로 PATCH 호출 시 상태 코드 | **404 Not Found**, 응답 형식은 기존 `AdminErrorResponse`와 동일한 `{"message": "..."}` 형태로 통일 | REST 관례상 경로 파라미터로 지정된 리소스가 없으면 404가 가장 명확함. 기존 에러 응답 형식과 통일해 프론트 에러 핸들링 로직 재사용 가능 |

---

## 공통 사항

| 항목 | 원안 | 실제 상태 |
|---|---|---|
| Base URL | `https://api.privacyguard.com/v1` | **미적용 (PoC 단계)**. 실제 배포 도메인 없음. 로컬 개발 서버는 `http://localhost:8080` (별도 context-path 설정 없음, 위 URL들이 곧 실제 경로) |
| 인증 방식 | Bearer Token (Authorization 헤더) | 일반 API(`companies`, `consents`, `risk-history` 등)는 여전히 인증 없음(PoC). 단, `/admin/**`는 Sprint 04(PR #27, `SecurityConfig.java`)에서 HTTP Basic + `ROLE_ADMIN` 인증이 추가됨 (2026-07-26) |
| 공통 응답 형식 | `{ "status": 200, "data": {...}, "message": "success" }` | **미적용**. 전부 `ResponseEntity<T>` 또는 `List<T>`를 그대로 반환하며 래핑 없이 응답 바디가 곧 `data`에 해당하는 내용이다. 예: `GET /companies`는 `{status:200,data:[...]}`가 아니라 `[...]` 배열을 바로 반환 |
| 테스트/데모 사용자 | (원안 없음) | ⚠️ **2026-07-30 기준 `users` 테이블이 완전히 비어 있음(0 rows)**. 시드 데이터 추가는 즉시 수정 대상(개발 프롬프트 A-1 참고). `userId`를 실제로 검증하는 건 PATCH(2-3)뿐이라 GET 계열(2-1, 2-2)은 존재하지 않는 userId로도 200 정상 응답한다 |

---

## 2-1. 기업 목록 조회 (위험도 순 정렬)

- **Method**: GET
- **URL**: `/companies`
- **설명**: 원안과 동일한 취지 — 사용자 맞춤(개인 맞춤) 위험도 높은 순으로 기업 목록 반환. 실제로는 `userId`가 체크한 선택동의 + 필수동의 기준으로 매 요청마다 서버가 실시간 재계산한다(저장된 배치 점수를 그대로 노출하지 않음).
- **Query Parameters**:

  | 이름 | 타입 | 필수 | 기본값 | 설명 |
  |---|---|---|---|---|
  | `userId` | Long | 필수 | - | 사용자 ID. ⚠️ **존재 검증 안 함**: DB에 없는 userId를 넣어도 200과 함께 정상 목록이 반환된다 |
  | `sort` | String | 선택 | `risk_score_desc` | 미사용 상태. 서버는 항상 위험도 내림차순만 지원. 향후 정렬 옵션 확장 필요 시 재검토 |

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
  - ⚠️ **문서 내 모순 (사실 확인 필요, 기능 영향 없음)**: 위 JSON 예시엔 `category: "SNS"`가 있고, 2-7(관리자 기업 등록)에서도 `category`가 `Company` 관련 요청/응답에 명확히 존재한다. 반면 v1 문서의 이전 검토 기록에는 "`Company` 엔티티에도 `CompanyRiskResponse` DTO에도 category가 전혀 없다"고 적혀 있어 서로 어긋난다. 가능한 설명은 category 컬럼 자체는 `Company` 엔티티에 존재하지만 `GET /companies`가 쓰는 `CompanyRiskResponse` DTO에만 매핑이 빠져 있는 경우다 — 정확한 사실은 코드 재확인이 필요하다(개발 프롬프트 A-0). **결정과 무관하게 이 필드는 있으면 매핑만 추가하는 선에서 정리한다(위 결정 사항 2번).**
  - `logoText`/`logoColor`는 둘 다 백엔드에 없는 것이 확실하다(2-7 어디에도 등장하지 않음). 안드로이드 앱 `frontend/app/src/main/java/com/dynamicconsent/data/remote/CompanyMapper.kt:33-37`에서 클라이언트가 임시로 채워 넣는다:
    ```kotlin
    category = "기타",                              // CompanyMapper.kt:33 — 위 모순 확인 후 실제 서버값 사용으로 전환 검토
    logoText = response.companyName.take(1),        // CompanyMapper.kt:36 (기업명 첫 글자)
    logoColor = DEFAULT_LOGO_COLOR,                  // CompanyMapper.kt:37 (고정값)
    ```
    로고 관련 필드 확장은 지금 하지 않기로 확정(위 결정 사항 2번).
  - `packageName`은 DB 컬럼상 nullable이다(`entity/Company.java:24`). null인 기업이 생기면 안드로이드 앱의 감시 대상 등록(`frontend/.../monitor/WatchedAppRegistry.kt:88-91`)에서 **에러 없이 조용히 스킵**되어 오버레이/감시 대상에서 빠진다. 기업 등록 시 packageName을 비워두지 않도록 운영 가이드에 명시할 것.

---

## 2-2. 기업 상세 조회

- **Method**: GET
- **URL**: `/companies/{company_id}`
- **상태**: **미구현 (원안은 통합형이었으나 실제로는 `/companies/{companyId}/consent-items`로 분리 구현됨, 확정된 최종 구조)**

원안은 기업 기본정보 + 동의 항목 전체 목록 + 위험도 점수를 한 응답에 통합해서 내려주는 단일 엔드포인트였다. 실제 코드에는 이 형태의 통합 엔드포인트가 없고, 대신 아래처럼 **역할이 분리된 두 엔드포인트**로 나뉘어 구현되어 있다. 이 분리 구조를 최종안으로 확정한다(통합 엔드포인트 재도입 안 함).

- 기업 기본정보 + 위험도 → 2-1 (`GET /companies`) 응답의 배열 원소 하나로 대체
- 동의 항목 전체 목록 → 아래 실제 대응 엔드포인트로 대체

### 실제 대응 엔드포인트: `GET /companies/{companyId}/consent-items`

- **Method**: GET
- **URL**: `/companies/{companyId}/consent-items`
- **설명**: 해당 기업의 필수+선택 동의 항목 전체를 5대 변수(DS/ES/TF/PC/AI) 점수와 함께 반환. `userId`가 체크한 선택동의 항목은 `checked: true`로 표시된다.
- **Path Parameters**: `companyId` (Long, 필수)
- **Query Parameters**: `userId` (Long, 필수) — 원안엔 없던 파라미터. ⚠️ 이 엔드포인트도 2-1과 동일하게 **userId 존재 여부를 검증하지 않는다**.
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
- **URL**: `/users/{userId}/consents/{consentItemId}`
- **설명**: 원안과 취지는 동일(선택동의 토글 후 위험도 즉시 재산출).
- **Request Body (v3 정정, 실제 코드 기준 확정)**: `ConsentPatchRequest`
  ```json
  { "checked": true }
  ```
  - **멱등(idempotent) 방식**: 원하는 최종 상태(`checked`)를 직접 지정한다. 같은 요청을 여러 번 보내도 결과가 항상 같아서, v1/v2에서 우려했던 "재시도/중복 탭 시 반대로 토글되는" 문제가 구조적으로 발생하지 않는다.
  - 재산출 기준은 동일: 필수동의 전체 + 이 사용자가 실제 체크한 선택동의만으로 계산(워스트 케이스 아님).
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
  - `changed_at`은 이 응답에 **포함하지 않기로 확정**한다(위 결정 사항 4번). 변경 이력이 필요하면 2-8을 사용할 것.
  - `newRiskGrade`는 2-1과 동일하게 실제 5단계 enum 값(`VERY_LOW`~`VERY_HIGH`) 기준.
- **에러 케이스 — 존재하지 않는 userId/consentItemId (확정, A-2에서 구현 완료)**: `ConsentApiService.toggleConsent()`가 사용자와 동의 항목의 존재를 각각 검증한다. **HTTP 404**로 응답하며, 형식은 공용 `ErrorResponse`로 통일: `{"message": "존재하지 않는 userId: {id}"}` / `{"message": "존재하지 않는 consentItemId: {id}"}`. 기존엔 처리 핸들러가 없어 HTTP 500이 나가던 상태였으나 해소됨(2026-08-25 실제 호출로 검증 완료, `ConsentApiControllerTest` 신규 2건).
- **프론트 중복 요청 방지 (이미 구현됨)**: body가 멱등이라 구조적으로 안전하지만, 추가 보호로 안드로이드 앱(`ConsentSyncManager.kt`, `OrgDetailViewModel.kt:109`)에 300ms 디바운스가 이미 적용돼 있어 동일 항목 재탭 시 이전 요청을 취소하고 마지막 상태만 전송한다.

---

## 2-4. 위험도 이력 조회

- **Method**: GET — 원안과 일치
- **URL**: `/users/{userId}/companies/{companyId}/risk-history`
- **설명**: "이 사용자의 개인 맞춤 대표 위험도"(`isRepresentative=true`, `user_id` NOT NULL) 이력만 반환한다.
- **Query Parameters**: **없음** — 원안의 `from`/`to`(기간 필터)는 아직 미구현. 현재는 필터 없이 날짜 오름차순 전체 반환(당장 급하지 않은 항목으로 보류, 4-10 그래프 구현 시 재검토).
- **응답**: `List<RiskScoreHistoryItemDto>`
- **응답 예시**:
  ```json
  [
    { "scoredAt": "2026-06-29", "totalScore": 87.4, "grade": "VERY_HIGH" },
    { "scoredAt": "2026-06-30", "totalScore": 72.1, "grade": "MEDIUM" }
  ]
  ```
- **저장 경로 (2026-08-08 배치 연결 완료)**:
  - **배치**: `PolicyCrawlProcessor.processCompany()`가 정책 변경 여부와 무관하게 매일 밤 무조건, 해당 기업에 `UserConsentCheck` row가 있는 사용자 전원에 대해 `PersonalRiskHistoryService.saveIfAbsent()`를 호출한다. 오늘자 row가 있으면 건너뛴다.
  - **PATCH 토글**: `ConsentApiService.toggleConsent()`가 매 호출마다 `PersonalRiskHistoryService.saveOrUpdateToday()`를 호출한다. 오늘자 row가 있으면 갱신, 없으면 신규 생성(같은 날 여러 번 토글해도 1건 유지).
  - 두 경로 모두 "오늘 날짜"로만 upsert하므로 과거 날짜 row는 덮어써지지 않는다(append-only).
- **알려진 제약**: 배치 대상은 "이 기업에 `UserConsentCheck` row가 있는 사용자"로 한정된다 — 한 번도 동의 화면을 연 적 없는 사용자는 배치 대상에서 제외된다. 프론트는 빈 배열을 에러가 아니라 "데이터 없음" UI로 처리해야 한다.

---

## 2-5. 약관 변경 알림 목록 조회

- **상태**: **구현 완료, 필터링 로직 확정 반영 (2026-08-25)**
- **관련 코드**: `notice/NoticeController.java`, `notice/NoticeResponse.java`, `repository/PolicySnapshotRepository`
- **Method**: GET
- **URL**: `/notices`
- **설명**: 전체 기업의 약관 스냅샷 중 **실제로 변경이 있었던 것만** 확인 시각(`crawledAt`) 내림차순으로 페이징 반환한다(공지사항 탭).
- **Query Parameters**: `page`(Int, 선택, 기본값 0), `size`(Int, 선택, 기본값 20)
- **필터링 (확정)**: 서버는 `isChanged=true`인 스냅샷만 반환하도록 한다. 이전에는 전체 기업의 최신 스냅샷을 매번 다 반환해서(변경 없어도 5개 기업이 매일 노출) 5-1(변경 감지 시 푸시)·5-2(변경 내역 탭)의 기획 의도와 어긋났다(위 결정 사항 1번). 전체 이력(변경 없는 것 포함) 조회가 나중에 필요해지면 그때 옵션 파라미터를 추가한다.
- **응답**: `List<NoticeResponse>` (배열, 래핑 없음, `isChanged=true`인 항목만)
- **응답 예시** (필터 적용 후 — 실제로 변경이 있었던 건만):
  ```json
  [
    { "companyId": 2, "companyName": "네이버", "crawledAt": "2026-07-30T20:13:55.408537", "isChanged": true }
  ]
  ```
  - ⚠️ **`crawledAt`은 "변경 시각"이 아니라 "확인 시각"이다.** 약관이 안 바뀌면 새 레코드 없이 기존 레코드의 `crawledAt`만 갱신되므로, 엄밀히는 "이 변경 상태가 마지막으로 확인된 시각"이다. 필터링 적용 이후엔 목록에 뜬 항목 자체가 실제 변경 건이므로 혼동 여지는 줄어든다.
  - **`isChanged` 필드명**: `NoticeResponse`는 class + `isChanged()` getter이며, `@JsonProperty("isChanged")`를 명시해야 Jackson이 `"changed"`로 깎지 않고 `isChanged`로 정확히 직렬화한다. 이미 반영되어 있음(문제 없음, 참고용).
  - **타임존**: `crawledAt`은 UTC가 아니라 **KST 그대로** 저장·반환된다. 서버 JVM 기본 타임존이 Asia/Seoul이라 별도 변환이 없다. Android에서 추가 타임존 변환을 하면 9시간이 이중으로 밀리니 주의.

---

## 2-6. 크롤링 수동 트리거 (관리자)

- **상태**: **구현됨** (`AdminCrawlController.triggerCrawl`, PR #24/#26/#27)
- **Method**: POST — 원안과 일치
- **URL**: `/admin/crawl/{companyId}`
- **인증**: `ROLE_ADMIN` 필요 (HTTP Basic). 미인증/오인증 시 401
- **설명**: 특정 기업 1건에 대해 크롤링 → 변경감지 → (최초 수집이거나 실제로 약관이 변경됐으면) 위험도 재산출까지 동기적으로 즉시 실행한다. 요청이 끝날 때까지 크롤링·LLM 호출·DB 저장이 전부 완료된 뒤 결과를 응답하므로, 관리자 콘솔은 넉넉한 타임아웃을 설정해야 한다.
- **응답**: `AdminCrawlTriggerResponse`
- **응답 예시 — 성공**:
  ```json
  { "companyId": 1, "companyName": "카카오", "changed": true, "riskAnalysisTriggered": true, "success": true, "message": "triggered" }
  ```
- **응답 예시 — 실패 (존재하지 않는 companyId, HTTP 404)**:
  ```json
  { "companyId": 999, "companyName": null, "changed": false, "riskAnalysisTriggered": false, "success": false, "message": "존재하지 않는 companyId: 999" }
  ```
  - 그 외 크롤링/LLM 호출 중 예외가 나면 HTTP 500 + 동일한 실패 형식으로 응답한다.

---

## 2-7. 관리자 기업 등록/삭제

- **상태**: **구현됨** (`AdminController`, PR #27)
- **인증**: `ROLE_ADMIN` 필요 (HTTP Basic)

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
  - `companyName`/`legalName`/`category`/`packageName`/`privacyUrl`은 필수(비어있으면 400)
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
  | `packageName` 중복 | 409 | `AdminErrorResponse` (`CompanyConflictException`) |

### `DELETE /admin/companies/{companyId}` — 기업 삭제

- **응답**: 성공 시 본문 없음 (HTTP 204)
- **설명**: `Company`에 `ConsentItem`/`PolicySnapshot`/`RiskScore`가 `cascade=ALL`로 걸려있어 삭제 시 연관 데이터가 통째로 같이 지워질 수 있다. 연관 데이터가 하나라도 있으면 삭제를 거부한다.
- **에러 케이스**:
  | 상황 | 상태 코드 | 응답 |
  |---|---|---|
  | 존재하지 않는 companyId | 404 | `AdminErrorResponse` (`IllegalArgumentException`) |
  | 연관 데이터가 하나라도 있음 | 409 | `AdminErrorResponse` (`CompanyConflictException`) |

### `AdminErrorResponse` 공통 에러 형식
```json
{ "message": "이미 등록된 packageName입니다: com.kakao.talk" }
```
> 이 형식을 2-3(PATCH)의 존재하지 않는 userId 404 응답에도 동일하게 적용한다(위 결정 사항 5번).

---

## 2-8. 동의 변경 이력 조회

- **상태**: **구현됨. 변경 이력의 단일 소스(Single Source of Truth)로 확정 (2026-08-25)**
- **관련 코드**: `consenthistory/UserConsentHistoryController.java`, `consenthistory/UserConsentHistoryService.java`, `consenthistory/UserConsentHistoryItemDto.java`, 테이블 `sql/migration/V5__create_user_consent_history.sql`
- **Method**: GET
- **URL**: `/users/{userId}/consents/history`
- **설명**: 2-3(PATCH 토글)이 발생할 때마다 `UserConsentHistoryRecorder`가 이 이력 테이블에 append-only로 한 건씩 기록한다.
- **Path Parameters**: `userId` (Long, 필수)
- **Query Parameters**: 없음
- **응답**: `List<UserConsentHistoryItemDto>` (변경 시각 오름차순, 배열, 래핑 없음)
- **응답 예시**:
  ```json
  [
    { "consentItemId": 268, "itemName": "마케팅 정보 수신 동의", "companyId": 1, "companyName": "카카오", "isChecked": true, "changedAt": "2026-07-29T10:15:00" },
    { "consentItemId": 268, "itemName": "마케팅 정보 수신 동의", "companyId": 1, "companyName": "카카오", "isChecked": false, "changedAt": "2026-07-29T14:02:00" }
  ]
  ```
  - 이 엔드포인트도 userId 존재 여부를 검증하지 않는다 — 이력이 없거나 존재하지 않는 userId면 빈 배열 `[]`을 200으로 반환한다.
- **확정**: 4-7(동의 변경 내역 탭)은 이 API를 이력의 단일 소스로 사용한다. PATCH(2-3) 응답에는 `changed_at`을 추가하지 않는다(위 결정 사항 4번).
