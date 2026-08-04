# Known Issues

## [해결됨] PolicySnapshot 저장과 위험도 재산출의 트랜잭션 경계 불일치

작업일: 2026-07-26 (발견) / 2026-07-30 (해결)
관련 코드: `PolicyCrawlProcessor.processCompany()`(신규), `PolicyCrawlScheduler`,
`PolicyChangeDetectionService.detectAndSave()`, `RiskPipelineService.analyzeAndSaveRisk()`
관련 테스트: `PolicyCrawlSchedulerTransactionBoundaryIntegrationTest`(단언을 반대로 뒤집어
"실패 시 스냅샷도 함께 롤백"을 검증하도록 갱신), `PolicyCrawlProcessorTest`(신규, 트리거 조건),
`PolicyCrawlSchedulerTest`(오케스트레이션만 검증하도록 축소)

### 증상 (해결 전)
`PolicyChangeDetectionService.detectAndSave()`는 그 자체로 `@Transactional`이라 스냅샷이
즉시 커밋됐다. `PolicyCrawlScheduler.processCompany()`는 그 자체는 트랜잭션이 아니라서,
스냅샷 저장 뒤에 실행되는 `riskPipelineService.analyzeAndSaveRisk()`(역시 별도
`@Transactional`)가 실패하면(LLM 재시도 소진 등) 이미 커밋된 스냅샷은 되돌릴 수 없었다.
다음 크롤링 때 이 커밋된 스냅샷과 해시가 같으면 "변경 없음"으로 판단되어, 놓친 위험도
재산출이 영구히 재시도되지 않는 문제가 있었다.

### 해결 내용
스냅샷 저장 + 위험도 재산출을 하나의 트랜잭션으로 묶었다(재설계 제안 1번 채택). 다만
단순히 `PolicyCrawlScheduler.processCompany()`에 `@Transactional`만 붙이는 걸로는 안 됐다 —
`runForCompany()`/`runPipeline()`이 같은 클래스 안에서 `processCompany()`를 내부 호출(자기호출,
self-invocation)하는 구조라, Spring의 프록시 기반 `@Transactional`이 조용히 무시된다(예외도
경고도 없음). 그래서 이 프로젝트의 기존 패턴(예: `ConsentApiController` → `ConsentApiService`처럼
트랜잭션 단위를 별도 빈으로 분리)을 그대로 따라, 신규 `PolicyCrawlProcessor` 빈으로
`processCompany()`를 옮기고 `PolicyCrawlScheduler`는 이를 주입받아 호출하는 얇은
오케스트레이터로 남겼다. `PolicyCrawlSchedulerTransactionBoundaryIntegrationTest`로 위험도
재산출 실패 시 스냅샷 저장까지 롤백되는 것을 실제 로컬 MySQL로 확인했다(`assertTrue(snapshot.isEmpty())`).

### 남은 트레이드오프 (재설계 제안 1번에서 이미 알려진 것, 낮은 우선순위)
크롤링(느림)과 LLM 호출(더 느림)이 한 트랜잭션 안에 들어가면서 그 시간만큼 DB 커넥션을
점유한다. 현재 규모(5개 기업, 새벽 배치 순차 처리)에서는 위험이 낮다고 판단했지만, 관리자
수동 트리거(`POST /admin/crawl/{id}`)가 배치와 동시에 여러 건 겹치는 경우 HikariCP 커넥션
풀(기본 10개, 별도 설정 없음)이 소진될 이론적 가능성은 남아있다 — **기업 수가 늘어나면
재검토 필요.** 재설계 제안 2번(`PolicySnapshot`에 `analysisStatus` 컬럼 추가해 트랜잭션 경계는
그대로 두고 "놓친 분석"만 재시도)은 스키마 변경이 필요해 채택하지 않았다.

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
  제약조건 위반 위험이 있기 때문. (2026-07-26 업데이트로 값 변경, 아래 참고)
- 토스: `privacy_url`을 `https://toss.im/privacy-policy`로 갱신은 했으나, 이 URL도
  크롤링이 안 되는 건 동일 (2026-07-19 기준 검증 제외 대상).

### 근본 해결을 위해 검토할 것
- Headless 브라우저(Playwright/Selenium 등) 도입해 JS 렌더링 후 텍스트 추출
- 또는 `raw_text` 길이/내용에 대한 최소 검증(예: N자 미만이면 실패로 재분류)을
  `CompanyPolicyCrawlService`에 추가해 최소한 silent failure는 걸러내기

### 업데이트 (2026-07-26) — 배달의민족 URL 교체 시도, 여전히 실패

`privacy_url`을 `https://www.woowahan.com/policy/10`(우아한형제들 본사 사이트)으로 변경하고
실제 `PolicyBodyCrawler.fetchCleanText()`를 그대로 호출해 재검증했다.

- **404 문제는 해결됨**: HTTP 200 정상 응답 (Cloudflare 경유, 차단/캡차 아님. 실제
  "우아한형제들" 페이지 맞음).
- **하지만 동일 계열의 새 문제로 여전히 실패**: 응답 본문이 `<div id="app"></div>`로
  완전히 빈 채 내려오고, 그 뒤에 `chunk-vendors.js`/`chunk-common.js` 같은 Vue SPA
  번들 스크립트만 붙어 있음. 실제 콘텐츠는 브라우저에서 JS 실행 후 클라이언트
  사이드로 렌더링되는 구조라 정적 HTML에는 텍스트가 없음.
- jsoup으로 추출 가능한 텍스트는 상단 네비게이션 메뉴 문구 158자 정도뿐이며, 이마저
  `PolicyBodyCrawler`의 노이즈 제거 셀렉터(`nav`, `.gnb` 등)에 걸러져 최종 0자.
  실행 결과: `PolicyCrawlException: ... 크롤링 결과 텍스트가 너무 짧습니다 (0자, 최소 100자 필요)`.
- **결론**: URL 문제 → HTML 구조(SPA) 문제로 실패 유형만 바뀌었을 뿐 크롤링은 여전히
  불가능. `www.woowahan.com` 도메인 자체가 Vue 기반 SPA라 이 도메인 하위 다른 경로를
  시도해도 같은 문제가 반복될 가능성이 높다.
- **현재 상태(당시)**: 팀 논의 결과 `privacy_url`은 새 값(`https://www.woowahan.com/policy/10`)으로
  유지하되, 배민은 계속 크롤링 실패 대상으로 기록. 근본 해결에는 아래 하이브리드 크롤러
  도입으로 해결됨(2026-07-26, 브랜치 `feature/hybrid-crawler`).

### 해결 (2026-07-26) — `PolicyBodyCrawler` 하이브리드(Jsoup → Playwright 폴백) 구조 도입

`PolicyBodyCrawler`를 다음과 같이 확장해 SPA 사이트도 자동으로 처리하도록 만들었다
(브랜치 `feature/hybrid-crawler`, 아직 develop 미병합):

1. 기존과 동일하게 Jsoup으로 먼저 시도 (빠르고 가벼움)
2. 추출된 텍스트가 최소 길이(100자) 미만이면 SPA로 판단하고, 헤드리스 브라우저
   (Playwright Java 1.61.0, Chromium)로 페이지를 실제 렌더링해 재시도
   (`Page.navigate(..., WaitUntilState.NETWORKIDLE)`로 JS 렌더링이 끝날 때까지 대기)
3. 렌더링된 HTML도 기존과 동일한 노이즈 제거 로직(`cleanText`)을 그대로 통과
4. 헤드리스로도 최소 길이 미달이면 기존과 동일하게 `PolicyCrawlException` 발생

**실제 재크롤링 결과 (2026-07-26, jshell로 실제 클래스 직접 호출해 검증)**

| 기업 | URL | 결과 | 수집 길이 | 경로 |
|---|---|---|---|---|
| 카카오 | kakao.com/ko/privacy | 성공 | 15,168자 | Jsoup만 (헤드리스 미호출) |
| 네이버 | policy.naver.com/rules/privacy.html | 성공 | 17,774자 | Jsoup만 (헤드리스 미호출) |
| 배달의민족 | woowahan.com/policy/10 | **성공(신규)** | 4,444자 | Jsoup 실패(0자) → 헤드리스 폴백 성공 |
| 토스 | toss.im/privacy-policy | **성공(신규)** | 13,435자 | Jsoup 실패(0자) → 헤드리스 폴백 성공 |
| 당근마켓 | privacy-policy.daangn.com | 성공 | 21,438자 | Jsoup만 (헤드리스 미호출) |

→ **5개 기업 전부 정상 수집 확인.** 배민/토스 모두 known_issues였던 SPA 빈 본문 문제가
헤드리스 렌더링으로 해결됨.

**소요 시간 실측 (같은 JVM 프로세스 내에서 5개 기업 전체 크롤링 기준)**

| 방식 | 5개 기업 총 소요시간 | 비고 |
|---|---|---|
| 기존(Jsoup 단독) | 1,535ms | 3곳 성공 + 2곳 실패(SPA, 0자) |
| 신규 하이브리드 — 브라우저 최초 기동 포함 | 13,174ms | 5곳 전부 성공, Chromium 최초 launch 비용 포함 |
| 신규 하이브리드 — 브라우저 이미 기동된 상태(웜) | 5,006ms | 5곳 전부 성공, 실제 반복 운영 시 대표값에 가까움 |

- 최초 1회만 Chromium 기동 비용(약 8~9초)이 들고, 이후에는 브라우저를 재사용하므로
  웜 상태 기준 전체 소요시간은 기존 대비 약 **3.3배(+3.5초)** 증가. 배민/토스 개별
  크롤링(웜 기준)은 각각 2.0초, 2.7초로 정적 크롤링(0.1초대)보다 느리지만 100자 미만
  실패보다는 명백히 낫다.
- 기존 3개 기업(카카오/네이버/당근마켓)은 하이브리드 적용 후에도 Jsoup 경로만 타며
  헤드리스 브라우저가 전혀 호출되지 않음을 코드로도(`headlessCalls` 카운터 테스트) 확인 —
  **회귀 없음.**

**테스트**: `PolicyBodyCrawlerTest`에 헤드리스 폴백 성공/실패 케이스, Jsoup 성공 시
헤드리스 미호출 검증 케이스를 추가(7→9건). 전체 unit 55건 + integration 12건 모두 통과.

**남은 참고사항**: 헤드리스 브라우저(Playwright Chromium) 바이너리가 크롤링 서버에
설치되어 있어야 한다(`playwright install chromium`, 약 300MB). 기업 수가 늘어나도 코드
변경 없이 동일 로직이 적용되므로 구조적으로는 확장 가능하나, SPA 사이트 비중이 늘면
브라우저 인스턴스 동시 처리량(현재는 단일 `Browser`를 순차 재사용) 튜닝이 필요할 수 있다.