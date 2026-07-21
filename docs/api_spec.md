# API 명세서

> 원본: Sprint 01(2026-06-29) 작성 "DB 스키마 & API 명세서 초안"
> 이 문서: Sprint 03(2026-07-21) 기준 실제 구현 코드 대조 후 갱신. 구조(2-1~2-6, 표 형식)는 원안을 그대로 유지하고, 내용만 실제 코드 기준으로 교체/보강했다.
> 대조 대상 코드: `backend/consentradar/src/main/java/com/consentradar/consentradar/api/ConsentApiController.java`, `.../riskhistory/RiskHistoryController.java`

---

## 공통 사항

| 항목 | 원안 | 실제 상태 |
|---|---|---|
| Base URL | `https://api.privacyguard.com/v1` | **미적용 (PoC 단계)**. 실제 배포 도메인 없음. 로컬 개발 서버는 `http://localhost:8080` (별도 context-path 설정 없음, 위 URL들이 곧 실제 경로) |
| 인증 방식 | Bearer Token (Authorization 헤더) | **미적용 (PoC 단계)**. Spring Security 등 인증 계층이 프로젝트에 없어 모든 엔드포인트가 인증 없이 호출 가능하다 |
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

- **상태**: **미구현 (계획 단계)**
- **Method**: GET
- **URL**: `/notices`
- **설명**: 크롤링 봇이 감지한 약관 변경 이력 목록 반환 (공지사항 탭)
- **응답 예시**: `{ "company_name": "카카오", "crawled_at": "2026-06-29T00:00:00Z", "is_changed": true }`
- **Query Parameters**: `page`(Int, 선택, 기본값 0), `size`(Int, 선택, 기본값 20)

(변경 감지 로직 자체는 `PolicyChangeDetectionService`로 백엔드에 구현/검증되어 있으나, 이를 노출하는 조회 API는 아직 없다.)

---

## 2-6. 크롤링 수동 트리거 (관리자)

- **상태**: **미구현 (계획 단계)**
- **Method**: POST
- **URL**: `/admin/crawl/{company_id}`
- **설명**: 특정 기업 대상 수동 크롤링 즉시 실행. 관리자 권한 필요
- **응답 예시**: `{ "status": "triggered", "company_id": 1, "triggered_at": "2026-06-29T10:00:00Z" }`

(크롤링 실행 로직 자체는 `PolicyCrawlScheduler.runPipeline()`으로 이미 분리되어 있어 컨트롤러만 얹으면 재사용 가능하지만, 관리자 인증/권한 체계가 없는 상태라 API 자체는 아직 미구현.)
