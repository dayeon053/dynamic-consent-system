# API 명세서

> 원본: Sprint 01(2026-06-29) 작성 "DB 스키마 & API 명세서 초안"
> 이 문서: Sprint 03(2026-07-21) 기준 실제 구현 코드 대조 후 갱신. 구조(2-1~2-6, 표 형식)는 원안을 그대로 유지하고, 내용만 실제 코드 기준으로 교체/보강했다.
> 대조 대상 코드: `backend/consentradar/src/main/java/com/consentradar/consentradar/api/ConsentApiController.java`, `.../riskhistory/RiskHistoryController.java`

---

## 공통 사항

| 항목 | 원안 | 실제 상태 |
|---|---|---|
| Base URL | `https://api.privacyguard.com/v1` | **미적용 (PoC 단계)**. 실제 배포 도메인 없음. 로컬 개발 서버는 `http://localhost:8080` (별도 context-path 설정 없음, 위 URL들이 곧 실제 경로) |
| 인증 방식 | Bearer Token (Authorization 헤더) | 일반 API(`companies`, `consents`, `risk-history` 등)는 여전히 인증 없음(PoC). 단, `/admin/**`는 Sprint 04(PR #27, `SecurityConfig.java`)에서 HTTP Basic + `ROLE_ADMIN` 인증이 추가됨 (2026-07-26) |
| 공통 응답 형식 | `{ "status": 200, "data": {...}, "message": "success" }` | **미적용**. 전부 `ResponseEntity<T>` 또는 `List<T>`를 그대로 반환하며 래핑 없이 응답 바디가 곧 `data`에 해당하는 내용이다. 예: `GET /companies`는 `{status:200,data:[...]}`가 아니라 `[...]` 배열을 바로 반환 |

---

## 2-1. 기업 목록 조회 (위험도 순 정렬)

- **Method**: GET
- **URL**: `/companies`
- **설명**: 원안과 동일한 취지 — 사용자 맞춤(개인 맞춤) 위험도 높은 순으로 기업 목록 반환. 실제로는 `userId`가 체크한 선택동의 + 필수동의 기준으로 매 요청마다 서버가 실시간 재계산한다(저장된 배치 점수를 그대로 노출하지 않음).
- **Query Parameters**:

  | 이름 | 타입 | 필수 | 기본값 | 설명 |
  |---|---|---|---|---|
  | `userId` | Long | 필수 | - | 사용자 ID (원안과 동일하게 필수) |
  | `sort` | String | 선택 | `risk_score_desc` | ⚠️ **알려진 이슈**: 파라미터를 받기는 하지만 컨트롤러 내부에서 실제로 사용되지 않는다. 값과 무관하게 항상 개인 맞춤 위험도 내림차순으로 고정 정렬된다 |

- **응답**: `List<CompanyRiskResponse>` (배열, 래핑 없음)
- **응답 예시** (실제 필드명 camelCase, 실제 등급 enum 값 기준):
  ```json
  [
    {
      "companyId": 1,
      "companyName": "카카오",
      "packageName": "com.kakao.talk",
      "privacyUrl": "https://www.kakaocorp.com/page/detail/9610",
      "ismsCertified": false,
      "riskScore": 43.5,
      "riskGrade": "VERY_HIGH"
    }
  ]
  ```
  - `riskGrade`는 원안의 `"DANGEROUS"` 같은 임의 문자열이 아니라 실제 5단계 enum(`VERY_LOW` / `LOW` / `MEDIUM` / `HIGH` / `VERY_HIGH`) 중 하나다.
  - 해당 기업에 동의 항목이 없어 위험도를 계산할 수 없으면 `riskScore`/`riskGrade`는 `null`로 내려간다.

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
- **Path Parameters**: `companyId` (Long, 필수)
- **Query Parameters**: `userId` (Long, 필수) — 원안엔 없던 파라미터. 사용자별 체크 상태(`checked`)를 판단하려면 실제 구현상 필수다.
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
- **알려진 제약**: 이력을 실제로 쌓는 저장 로직(`PersonalRiskHistoryService.saveIfAbsent()`)은 구현되어 있지만, 이걸 언제·어디서 호출할지(2-3 토글 시마다 vs 별도 배치)가 아직 확정/연결되지 않은 상태다(코드 내 TODO로 명시됨). 따라서 현재 시점엔 이 엔드포인트를 호출해도 실제로 쌓인 이력이 없을 수 있다.

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

- **상태**: **구현됨** (`AdminCrawlController.triggerCrawl`, PR #24/#26/#27)
- **Method**: POST — 원안과 일치
- **URL**: `/admin/crawl/{companyId}` (원안의 `company_id`가 실제로는 camelCase `companyId`)
- **인증**: `ROLE_ADMIN` 필요 (HTTP Basic — 공통 사항 참고). 미인증/오인증 시 401
- **설명**: 특정 기업 1건에 대해 크롤링 → 변경감지 → (최초 수집이거나 실제로 약관이 변경됐으면) 위험도 재산출까지 동기적으로 즉시 실행한다(`PolicyCrawlScheduler.runForCompany`). 원안의 "비동기 트리거 후 상태만 반환" 방식이 아니라, 요청이 끝날 때까지 크롤링·(필요 시) LLM 호출·DB 저장이 전부 완료된 뒤 결과를 응답한다.
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
  - `changed`: 이번 크롤링에서 전일 대비 약관 변경이 감지됐는지
  - `riskAnalysisTriggered`: 최초 수집이거나 `changed=true`라서 LLM 재분석까지 실행했는지 (변경이 없으면 `false`이고 크롤링 확인만 하고 끝남)
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
    "packageName": "com.kakao.talk",
    "privacyUrl": "https://www.kakaocorp.com/page/detail/9610",
    "ismsCertified": false
  }
  ```
  - `companyName`/`packageName`/`privacyUrl`은 필수(비어있으면 400)
- **응답**: `CompanyResponse` (HTTP 201)
  ```json
  {
    "companyId": 6,
    "companyName": "카카오",
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
