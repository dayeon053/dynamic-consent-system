# Known Issues

## [열려있음] PolicySnapshot 저장과 위험도 재산출의 트랜잭션 경계 불일치

작업일: 2026-07-26
관련 코드: `PolicyCrawlScheduler.processCompany()`, `PolicyChangeDetectionService.detectAndSave()`,
`RiskPipelineService.analyzeAndSaveRisk()`
관련 테스트: `PolicyCrawlSchedulerTransactionBoundaryIntegrationTest`(회귀 감지용, 재설계는 미구현)

### 증상
`PolicyChangeDetectionService.detectAndSave()`는 그 자체로 `@Transactional`이라 스냅샷이
즉시 커밋된다. `PolicyCrawlScheduler.processCompany()`는 그 자체는 트랜잭션이 아니라서,
스냅샷 저장 뒤에 실행되는 `riskPipelineService.analyzeAndSaveRisk()`(역시 별도
`@Transactional`)가 실패해도(LLM 재시도 소진 등) 이미 커밋된 스냅샷은 되돌릴 수 없다.

더 심각한 파급효과: 다음 크롤링 때 이 커밋된 스냅샷과 SHA-256 해시를 비교하므로, 그 사이
내용이 더 안 바뀌었다면 `isChanged()=false`가 되어 **이번에 놓친 위험도 재산출이 영구히
재시도되지 않는다.** LLM 일시 장애였을 뿐인데 그 정책 변경에 대한 분석 기회 자체가
조용히 소비돼버리는 셈이다.

### 재설계 제안 (구현 안 함 — 다연과 논의 후 결정)
1. **스냅샷 저장 + 위험도 재산출을 하나의 트랜잭션으로 묶기**: `processCompany()`에
   `@Transactional`을 부여(또는 별도 오케스트레이션 메서드로 묶기). 가장 간단하지만,
   크롤링(느림)과 LLM 호출(더 느림)이 한 트랜잭션 안에 들어가면서 DB 커넥션을 오래
   점유하게 되는 트레이드오프가 있다.
2. **PolicySnapshot에 분석 상태 컬럼 추가**(예: `analysisStatus`: PENDING/SUCCESS/FAILED):
   해시가 같아도 이전 분석이 FAILED였으면 재시도하도록 `isChanged()` 판단 로직을 확장.
   트랜잭션 경계는 그대로 유지하면서 "놓친 분석"을 다음 크롤링 때 다시 시도할 수 있게
   해준다. 스키마 변경이 필요하다.

현재는 회귀 감지 테스트만 추가해뒀다 — 지금 이렇게 동작한다는 걸 문서화한 것이지,
이게 맞는 동작이라는 뜻은 아니다.

---

## [해결됨] ConsentItem 동시 요청 레이스 — DB unique 제약 + upsert 서비스로 방지

작업일: 2026-07-26
관련 코드: `ConsentItemUpsertService`(신규), `ConsentItemRepository`, `RiskPipelineService`,
`ConsentItemBatchService`, 마이그레이션 `V4__add_unique_constraint_consent_item_company_item_name.sql`

### 배경
바로 아래 항목("ConsentItem 중복 insert 버그")을 itemName 기준 upsert로 고친 뒤에도,
"조회 후 insert"라는 애플리케이션 레벨 로직만으로는 두 요청이 거의 동시에 들어오는 경우
(인증 없는 `POST /admin/crawl/{companyId}` 더블클릭, 또는 관리자 트리거와 스케줄러 실행이
겹치는 경우)를 막을 수 없었다. `ConsentItemBatchService.saveAll()`도 같은 패턴(dedup 없이
무조건 insert)이라 동일한 위험이 있었다(다만 이 메서드는 현재 어디서도 호출되지 않는
상태라 실질 영향은 없었음).

### 조치
1. `consent_item(company_id, item_name)`에 DB UNIQUE 제약 추가 — 동시 요청이 겹쳐도 DB가
   최종적으로 중복 row를 막아준다.
2. 두 서비스가 공유하는 `ConsentItemUpsertService`를 새로 만들어 itemName 기준 조회 →
   있으면 update, 없으면 insert 로직을 한 곳에 모았다.
3. **시도했다가 되돌린 것**: insert가 unique 제약 위반으로 실패하면 같은 트랜잭션 안에서
   재조회 후 update로 전환하는 "우아한 fallback"을 처음에 구현했으나, 실제로 통합
   테스트에서 `org.hibernate.AssertionFailure: ... null identifier (this can happen if
   the session is flushed after an exception occurs)`가 재현됐다. Hibernate 세션은 flush
   실패 후 계속 쓰기에 안전하지 않다는 걸 실측으로 확인한 것 — 그래서 이 fallback은
   제거하고, unique 제약 위반은 그대로 던져 트랜잭션 전체가 롤백되게 뒀다. 레이스에 진
   요청은 500으로 실패하고 재시도하면 된다. DB 제약이 실질적인 안전장치이고, 이건
   의도적으로 단순하게 남겨둔 것이다.

## [해결됨] RiskPipelineService가 스케줄러에 연결된 뒤 드러난 ConsentItem 중복 insert 버그

작업일: 2026-07-26
관련 코드: `RiskPipelineService.analyzeAndSaveRisk()`, `PolicyCrawlScheduler.processCompany()`
관련 PR: #24 (다연 리뷰)

### 배경 — 이전 서술 정정
과거 이 문서/CLAUDE.md에는 "`RiskPipelineService`는 스케줄러에서 실제로 호출되지 않는
죽은 코드"라고 적혀 있었다(스프린트2 시점 감사 기준). 이는 더 이상 사실이 아니다.
`PolicyCrawlScheduler.processCompany()`가 이미 `riskPipelineService.analyzeAndSaveRisk()`를
호출하도록 연결되어 있고, 최초 수집이거나 약관이 실제로 변경된 기업에 한해 매일 새벽 3시
스케줄러 실행 시 자동으로 트리거된다.

### 증상
`analyzeAndSaveRisk()`가 매 호출마다 `ConsentItem`을 조건 없이 `new` + `save()`만 해서,
같은 회사에 대해 약관 변경이 감지될 때마다 `ConsentItem`이 중복 insert됐다. 스케줄러가
실제로 연결되어 있었기 때문에 이건 이론상 문제가 아니라 **매일 새벽 3시 실행마다 실제로
발생하던 라이브 버그**였다. 단순히 기존 항목을 삭제 후 재삽입하는 방식으로 고치면
`ConsentItem`—`UserConsentCheck` 간 `@OneToMany(cascade = ALL)` 때문에 사용자 동의
이력(`UserConsentCheck`)까지 함께 삭제되는 부작용이 있어 그 방식은 사용할 수 없었다.

### 조치
`analyzeAndSaveRisk()`를 itemName 기준 upsert로 수정(PR #24): 기존 `ConsentItem`이 있으면
해당 row를 재사용해 `itemType`/`ds`/`es`/`tf`/`pc`/`ai` 점수만 갱신하고, 없으면 새로 생성한다.
삭제 후 재삽입이 아니므로 `UserConsentCheck`는 보존된다. 재현 테스트
(`RiskPipelineServiceIntegrationTest`)로 중복 생성 안 됨 + `UserConsentCheck` 생존을 검증함.

### 범위 밖 (TODO)
이번 크롤링 결과에 더 이상 나타나지 않는 예전 `ConsentItem`(회사가 약관에서 뺀 항목)을
삭제할지 만료 플래그로 남길지는 이번 수정 범위 밖으로 남겨뒀다 — 별도로 논의 후 결정.

---

## 배달의민족·토스 개인정보처리방침 페이지 — Jsoup 크롤링 불가 (SPA 구조)

작업일: 2026-07-19
관련 코드: `CompanyPolicyCrawlService`, `PolicyBodyCrawler`

### 증상
로컬 MySQL의 실제 기업 5건(카카오/네이버/배달의민족/토스/당근마켓)을 대상으로
`CompanyPolicyCrawlService.crawlAll()`을 실제 네트워크로 검증한 결과, 카카오/네이버/당근마켓은
정상적으로 본문 텍스트가 수집됐지만 배달의민족과 토스는 실패했다.

- **배달의민족** (`www.baemin.com`, `terms.baemin.com`): HTTP 404. 사이트 자체가
  React/Next.js 기반 SPA라서 `<div id="root"></div>` + JS 번들만 있고 정적 HTML에
  실제 텍스트가 없음. 크롤링 가능한 정적 URL을 찾지 못함 (동일 법인인 우아한형제들이
  운영하는 "배민문방구" 스토어 정책 페이지(`store.baemin.com/service/private.php`)는
  정적 HTML로 존재하지만, 배달앱 자체의 정책 문서와 동일한지 확인되지 않아 채택하지 않음).
- **토스** (`toss.im/privacy-policy`): HTTP 200으로 응답하지만 Jsoup으로 추출한
  본문(`body.text()`)이 zero-width 문자 35자뿐인 사실상 빈 텍스트. 이 페이지도 Next.js
  SPA이며, 실제 정책 텍스트는 `<script id="__NEXT_DATA__">` JSON 안에만 존재하고
  화면에 정적으로 렌더링되지 않음. `curl | grep`으로는 "개인정보처리방침" 문자열이
  잡혀서 처음엔 정상 URL로 오판했으나, 그건 저 JSON 블록 안의 문자열이었을 뿐
  실제 렌더링된 본문이 아니었다.

### 영향
`CompanyPolicyCrawlService.crawlAll()`은 예외가 안 나면 무조건 성공으로 집계하므로,
토스 같은 케이스(HTTP 200 + 빈 본문)는 **배치 결과상 성공으로 잡히지만 실제로는
의미 있는 데이터가 저장되지 않는 silent failure**다. 현재는 raw_text 내용 검증
로직이 없다.

### 임시 조치
- 배달의민족: `privacy_url`을 기존 값(`https://www.baemin.com/policy/privacy`, 404) 그대로 유지.
  NULL로 바꾸지 않은 이유는 `Company.privacyUrl`이 `nullable = false`라서 NULL 저장 시
  제약조건 위반 위험이 있기 때문.
- 토스: `privacy_url`을 `https://toss.im/privacy-policy`로 갱신은 했으나, 이 URL도
  크롤링이 안 되는 건 동일 (2026-07-19 기준 검증 제외 대상).

### 근본 해결을 위해 검토할 것
- Headless 브라우저(Playwright/Selenium 등) 도입해 JS 렌더링 후 텍스트 추출
- 또는 `raw_text` 길이/내용에 대한 최소 검증(예: N자 미만이면 실패로 재분류)을
  `CompanyPolicyCrawlService`에 추가해 최소한 silent failure는 걸러내기