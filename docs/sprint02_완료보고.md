# Sprint 02 완료 보고

대상 프로젝트: `backend/consentradar` (Spring Boot 4.1 / Java 23 / Gradle, MySQL)
공통 모듈: `common-model` (RiskCalculator, LLM 파싱/재시도 등 기존 자산 재사용)

---

## 1. RiskScore is_representative 컬럼 추가

**구현 내용 요약**
RiskScore 엔티티에 `is_representative` boolean 필드를 추가하고, 파이프라인에서 동의 항목별 점수는 `false`, 기업 대표(종합) 점수는 `true`로 세팅하도록 반영했다. Flyway/Liquibase가 프로젝트에 없어(`ddl-auto: create`로 운영) DDL 스크립트만 별도로 작성했다.

**생성/수정 파일**
- 수정: `src/main/java/com/consentradar/consentradar/entity/RiskScore.java`
- 수정: `src/main/java/com/consentradar/consentradar/pipeline/RiskPipelineService.java`
- 생성: `src/main/resources/sql/migration/V1__add_is_representative_to_risk_score.sql`

**완료 기준 충족 여부**
| 기준 | 충족 |
|---|---|
| `is_representative BOOLEAN DEFAULT false` 컬럼 추가 | O |
| 항목별=false, 대표=true 세팅 | O |
| JPA 엔티티 반영 | O |
| 마이그레이션 스크립트(또는 DDL) 반영 | O (Flyway/Liquibase 미사용 확인 후 순수 DDL로 작성) |

**테스트/검증 방법**
- `./gradlew.bat compileJava compileTestJava` 로 컴파일 확인
- 이후 태스크 4의 파이프라인 테스트에서 `RiskPipelineService`가 대표 점수에 `isRepresentative=true`를 세팅하는 동작을 간접 검증 (직접 단위 테스트는 별도 추가하지 않음)

**남은 이슈/TODO**
- 없음. 단, `RiskPipelineService`의 대표 점수 산출 로직(최고 점수 기준)을 검증하는 전용 단위 테스트는 이번 스프린트 범위에 포함하지 않았음 — 필요 시 추가 권장.

---

## 2. 크롤링 구현 (Jsoup)

**구현 내용 요약**
전체 Company를 조회해 `privacy_url`을 순회하며 Jsoup으로 본문 텍스트를 크롤링하는 컴포넌트를 작성했다. nav/footer/script/style/header/.ad/.banner 등 잡음 태그를 제거한 뒤 본문 텍스트만 추출하며, 요청 실패 시 3회(1s→2s→4s backoff) 재시도한다.

**생성/수정 파일**
- 생성: `src/main/java/com/consentradar/consentradar/crawler/PolicyBodyCrawler.java`
- 생성: `src/main/java/com/consentradar/consentradar/crawler/CompanyPolicyCrawlService.java`
- 생성: `src/main/java/com/consentradar/consentradar/crawler/CrawlBatchResult.java`
- 생성: `src/main/java/com/consentradar/consentradar/crawler/PolicyCrawlException.java`
- 생성(테스트): `src/test/java/com/consentradar/consentradar/crawler/PolicyBodyCrawlerTest.java`
- 생성(테스트): `src/test/java/com/consentradar/consentradar/crawler/CompanyPolicyCrawlServiceTest.java`

**완료 기준 충족 여부**
| 기준 | 충족 |
|---|---|
| Company 전체 조회 후 privacy_url 순회 | O |
| Jsoup 본문 텍스트 크롤링 | O |
| 광고/nav/footer 등 제거 후 본문만 추출 | O |
| 실패 시 3회 재시도, 1s→2s→4s backoff | O |
| PolicySnapshot.raw_text 저장, crawled_at=현재시각 | O |
| is_changed 이 시점엔 미설정 | O (기본값 false 유지, 태스크3에서 처리) |
| 최소 5개 기업 텍스트 수집 성공 확인 가능 | O (단, 아래 검증 방법 참고 — 실제 네트워크가 아닌 Mock 기반) |

**테스트/검증 방법**
- `PolicyBodyCrawlerTest`: `Jsoup.parse()`로 만든 샘플 HTML에 대해 `cleanText()`가 nav/footer/script/style/header/.ad/.banner 텍스트를 제거하고 본문만 남기는지 검증. `connectAndGet()`을 오버라이드해 재시도 3회 동작(실패만 반복 시 3회 후 예외, 2회 실패 후 성공 시 3회째 성공)을 검증.
- `CompanyPolicyCrawlServiceTest`: Mockito로 `CompanyRepository.findAll()`이 기업 5개를 반환하도록 설정하고, `PolicyBodyCrawler`를 mock으로 대체해 5건 모두 성공(및 1건 실패 케이스)을 검증. **실제 네트워크 크롤링은 로컬 환경 제약(샌드박스 네트워크 격리 가능성)상 자동화 테스트에 포함하지 않았고, mock으로 5개 기업 처리 흐름/로그/카운트를 검증했다.**
- 실행 방법(주1 참고): 표준 `./gradlew.bat test` 대신 아래 우회 절차로 실행.
  ```
  ./gradlew.bat -I <classpath-dump-init.gradle> printTestCp
  "<JDK23>/bin/javac" -cp <testRuntimeClasspath> -d out TestRunnerMain.java
  "<JDK23>/bin/java" -cp <testRuntimeClasspath>;out TestRunnerMain
  ```
  결과: 신규 테스트 5개(PolicyBodyCrawlerTest 3개 + CompanyPolicyCrawlServiceTest 2개) 전부 통과.

**남은 이슈/TODO**
- 실제 회사 privacy_url에 대한 실환경 크롤링(라이브 네트워크) 검증은 아직 수행하지 않음 — DB에 기업이 등록된 후 `CompanyPolicyCrawlService.crawlAll()`을 실제로 1회 실행해 확인 필요.

---

## 3. 변경 감지 로직

**구현 내용 요약**
새로 크롤링한 raw_text를 SHA-256으로 해시하여 해당 기업의 최신 PolicySnapshot(raw_text 재계산 해시)과 비교한다. 다르면 새 레코드를 insert(`is_changed=true`)하고, 같으면 새 레코드 없이 기존 최신 레코드의 `crawled_at`만 갱신한다.

**생성/수정 파일**
- 생성: `src/main/java/com/consentradar/consentradar/crawler/PolicyTextHasher.java`
- 생성: `src/main/java/com/consentradar/consentradar/crawler/PolicyChangeDetectionService.java`
- 수정: `src/main/java/com/consentradar/consentradar/entity/PolicySnapshot.java` (`crawledAt`의 `updatable=false` 제거 — 변경 없음 케이스에서 갱신이 가능하도록 수정)
- 수정: `src/main/java/com/consentradar/consentradar/repository/PolicySnapshotRepository.java` (최신 스냅샷 조회 메서드 추가)
- 생성(테스트): `src/test/java/com/consentradar/consentradar/crawler/PolicyChangeDetectionServiceTest.java`

**완료 기준 충족 여부**
| 기준 | 충족 |
|---|---|
| raw_text SHA-256 해시 변환 | O |
| 최신 레코드 해시(미저장 시 raw_text로 재계산) 비교 | O (해시 컬럼을 별도로 두지 않아 항상 raw_text로 재계산) |
| 다르면 새 레코드 insert + is_changed=true | O |
| 같으면 insert 없이 crawled_at만 갱신 | O |
| 단위 테스트: 변경 있음/없음 각각 작성 | O (+ 최초 수집 baseline 케이스 추가) |

**테스트/검증 방법**
- `PolicyChangeDetectionServiceTest`에 3케이스: 텍스트 변경 시 insert+is_changed=true 검증, 텍스트 동일 시 save가 기존 엔티티 1회만 호출되고 crawledAt만 갱신되는지 검증, 최초 수집(이전 스냅샷 없음) 시 baseline insert 검증.
- 실행 방법은 태스크2와 동일한 우회 절차 사용. 결과: 신규 테스트 3개 전부 통과.

**남은 이슈/TODO**
- 없음.

---

## 4. 스케줄러 연동

**구현 내용 요약**
`@Scheduled(cron="0 0 3 * * *")`로 매일 새벽 3시에 크롤링(태스크2)+변경감지(태스크3)를 묶은 파이프라인을 실행한다. 실행 로직은 `runPipeline()`으로 분리해 관리자 API 등에서 수동 트리거로 재사용할 수 있게 했고, 시작/종료 시각·처리 기업 수·성공/실패 건수를 logger로 기록한다.

**생성/수정 파일**
- 수정: `src/main/java/com/consentradar/consentradar/ConsentradarApplication.java` (`@EnableScheduling` 추가)
- 생성: `src/main/java/com/consentradar/consentradar/scheduler/PolicyCrawlScheduler.java`
- 생성: `src/main/java/com/consentradar/consentradar/scheduler/PipelineRunResult.java`
- 생성(테스트): `src/test/java/com/consentradar/consentradar/scheduler/PolicyCrawlSchedulerTest.java`

**완료 기준 충족 여부**
| 기준 | 충족 |
|---|---|
| `@Scheduled(cron="0 0 3 * * *")`로 크롤링+변경감지 전체 실행 | O |
| 매 실행마다 시작/종료 시각, 처리 기업 수, 성공/실패 건수 로그 | O (SLF4J logger 사용) |
| 별도 실행 로그 엔티티 필요 시 제안 | O — 아래 제안 참고 (구현은 범위 외로 보류) |
| 수동 트리거 가능하도록 메서드 분리(관리자 API 재사용 대비) | O (`runPipeline()` public 메서드) |

**테스트/검증 방법**
- `PolicyCrawlSchedulerTest`: `CompanyRepository`/`PolicyBodyCrawler`/`PolicyChangeDetectionService`를 mock으로 대체해 `runPipeline()`이 전체 기업 수·성공/실패 건수를 정확히 집계하고, 실패 1건이 있어도 나머지 처리를 계속하는지 검증.
- 실행 방법은 동일한 우회 절차. 결과: 신규 테스트 2개 전부 통과.
- `scheduledRun()`(cron 트리거 자체)은 실제 스케줄 대기가 필요해 자동화 테스트 대상에서 제외하고, 이를 호출하는 `runPipeline()`만 단위 테스트로 검증함.

**남은 이슈/TODO**
- **제안**: 현재는 로그(logger)만 남기므로 서버 재시작 시 과거 실행 이력이 사라진다. 운영 모니터링이 필요하다면 `PipelineRunLog` 엔티티(`startedAt`, `finishedAt`, `totalCompanies`, `successCount`, `failCount`)를 추가해 매 실행 결과를 DB에 적재하는 것을 권장한다. 이번 스프린트에서는 요청 범위(제안까지)에 따라 구현하지 않았다.
- 관리자 API(수동 트리거 엔드포인트) 자체는 이번 스프린트 범위에 없어 미구현 — `PolicyCrawlScheduler.runPipeline()`을 그대로 주입해 컨트롤러에서 호출하면 됨.

---

## 5. ConsentItem 배치 insert

**구현 내용 요약**
LLM 파싱 결과를 가정한 `ConsentItemDto` 리스트를 받아 `@Transactional`로 배치 저장하는 서비스를 작성했다. 저장 전 ds/es/tf/pc/ai_score가 0~10 범위인지 전부 검증하고, 하나라도 벗어나면 예외를 던져 전체 롤백되도록 했다.

**생성/수정 파일**
- 생성: `src/main/java/com/consentradar/consentradar/consentitem/ConsentItemDto.java`
- 생성: `src/main/java/com/consentradar/consentradar/consentitem/InvalidConsentItemScoreException.java`
- 생성: `src/main/java/com/consentradar/consentradar/consentitem/ConsentItemBatchService.java`
- 생성(테스트): `src/test/java/com/consentradar/consentradar/consentitem/ConsentItemBatchServiceTest.java`

**완료 기준 충족 여부**
| 기준 | 충족 |
|---|---|
| `List<ConsentItemDto>` 배치 insert | O |
| `@Transactional`로 하나라도 실패 시 전체 롤백 | O |
| 저장 전 0~10 범위 검증, 벗어나면 예외+롤백 | O |

**테스트/검증 방법**
- `ConsentItemBatchServiceTest`: 정상 케이스(2건 배치 저장), 범위 초과 케이스(`InvalidConsentItemScoreException` 발생 및 `saveAll()` 미호출로 저장 안 됨 확인), 존재하지 않는 companyId 케이스(`IllegalArgumentException`) 3가지 검증.
- 실행 방법은 동일한 우회 절차. 결과: 신규 테스트 3개 전부 통과.
- `@Transactional` 롤백 자체는 실제 DB 트랜잭션 매니저가 필요해 Mockito 단위 테스트로는 "예외 발생 시 save 미호출"까지만 검증했고, 실제 DB 트랜잭션 롤백 동작은 통합 테스트(실 MySQL 연결) 대상으로 별도 확인이 필요하다.

**남은 이슈/TODO**
- 실 DB 연결 기반 `@DataJpaTest`/통합 테스트로 트랜잭션 롤백을 재검증하는 것을 권장 (현재 로컬 MySQL 미가동 이슈로 보류, 아래 공통 이슈 참고).

---

## 공통 이슈: 테스트 실행 환경

1. **Gradle `test` 태스크 클래스패스 오류**: 프로젝트 경로에 OneDrive + 한글/공백(`바탕 화면`)이 포함되어 있어, Gradle 9.5.1의 테스트 워커가 컴파일된 테스트 클래스를 `ClassNotFoundException`으로 찾지 못하는 환경 이슈가 있다. 이 스프린트 이전부터 존재하던 문제로(기존 `ConsentradarApplicationTests`도 동일 증상), 신규로 작성한 코드와는 무관하다. 우회책으로 Gradle init 스크립트로 `testRuntimeClasspath`를 덤프한 뒤 JUnit Platform Launcher를 직접 `java`로 실행해 검증했다. **정식 해결을 원하면 프로젝트를 공백/비-ASCII 문자가 없는 경로로 옮기는 것을 권장.**
2. **`ConsentradarApplicationTests.contextLoads()` 실패**: 로컬 MySQL(`localhost:3306`)이 현재 가동되어 있지 않아(`Connection refused`) 스프링 컨텍스트 로딩이 실패한다. 코드 문제가 아니라 환경 문제이며, MySQL 서버를 재기동한 뒤 재검증이 필요하다.

---

## 전체 요약

| 태스크 | 상태 | 비고 |
|---|---|---|
| 1. RiskScore is_representative 컬럼 추가 | 완료 | 엔티티/파이프라인/DDL 스크립트 반영, 전용 단위 테스트는 없음 |
| 2. 크롤링 구현 (Jsoup) | 완료 | 실제 네트워크 크롤링 검증은 미수행(로컬 환경 제약), mock 기반 테스트로 로직 검증 |
| 3. 변경 감지 로직 | 완료 | 변경/무변경/최초수집 3케이스 테스트 통과 |
| 4. 스케줄러 연동 | 완료 | 실행 로그 엔티티는 구현 대신 제안만 함, 관리자 API 자체는 범위 외 |
| 5. ConsentItem 배치 insert | 완료 | 트랜잭션 롤백은 mock 기반으로만 검증, 실 DB 통합 테스트 권장 |

**공통 남은 이슈**: (1) Gradle `test` 태스크가 프로젝트 경로의 한글/공백 문제로 실행 불가 — 우회 실행으로 검증함, (2) 로컬 MySQL 미가동으로 `contextLoads` 통합 테스트 미통과 — 서버 재기동 후 재검증 필요.
