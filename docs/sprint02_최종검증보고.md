# Sprint 02 최종 검증 보고

대상 프로젝트: `backend/consentradar` (Spring Boot 4.1 / Java 23 / Gradle, MySQL)
관련 문서: [sprint02_완료보고.md](./sprint02_완료보고.md) (구현 완료 시점 보고), [known_issues.md](./known_issues.md) (SPA 크롤링 불가 이슈 상세)
브랜치: `feature/backend-db-schema`

이 문서는 `sprint02_완료보고.md`에서 "실 DB/실 네트워크 검증 필요"로 남겨뒀던 항목들을
로컬 MySQL(`consentradar` DB)과 실제 네트워크로 직접 검증하고, 그 과정에서 발견된 문제를
수정한 내역을 정리한다.

---

## 1. 환경 정리

**프로젝트 경로**
`sprint02_완료보고.md`의 "공통 이슈"에 기록되어 있던 OneDrive + 한글/공백 경로(`바탕 화면` 등)
문제로 인해 당시에는 Gradle 테스트 워커가 `ClassNotFoundException`을 내는 환경이었고,
Gradle init 스크립트로 클래스패스를 덤프해 `java`로 직접 JUnit을 실행하는 우회책을 썼다.

이번 세션 시작 시점에는 프로젝트가 이미 `C:\consentradar\consentradar\backend\consentradar`
경로로 이동되어 있었고, `./gradlew.bat test`를 표준 방식 그대로 실행해도 문제없이 동작함을
세션 내내 반복 확인했다 (더 이상 별도 우회 절차 불필요).

**루트의 죽은 예전 스캐폴딩 정리**
레포 루트(`C:\consentradar\consentradar`)를 조사한 결과 두 가지 문제를 발견해 정리했다.

1. **루트에 남아있던 예전 Spring Boot 프로젝트 잔재**: `src/`, `build.gradle`,
   `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/`, `HELP.md` — 전부 git으로
   추적되던 파일이었지만 마지막 커밋이 2026-07-10로 9일간 정지 상태였고, `backend/consentradar/`
   쪽의 완전한 하위호환 버전(파일 수 15개 vs 37개)이었다. 실제 빌드는 전부
   `backend/consentradar/`에서 이뤄지고 있었음을 git log로 확인 후 `git rm`으로 제거.
2. **레포 자기 자신이 통째로 들어있던 중첩 클론**: `dynamic-consent-system/` 폴더가 자체
   `.git`을 가진 상태로 gitlink(mode 160000, `.gitmodules` 없는 "고아" 서브모듈)로 등록돼
   있었다. 내부 `git remote -v` 확인 결과 메인 레포와 완전히 동일한 원격 주소를 가리키고
   있었고, 커밋되지 않은 변경사항·미푸시 커밋이 없음을 확인한 뒤 안전하게 삭제.

커밋: `a57a3d8` — `chore: 중복 클론 폴더(dynamic-consent-system) 및 죽은 예전 스캐폴딩 제거`

---

## 2. develop 브랜치 병합

`git merge origin/develop`을 `feature/backend-db-schema`로 진행하는 과정에서
`RiskPipelineService.java` 1개 파일에 both-added 충돌이 발생했다.

**충돌 내용**: 실제 diff는 2줄뿐이었다.
```java
riskScore.setRepresentative(false);      // HEAD에만 존재
companyRiskScore.setRepresentative(true); // HEAD에만 존재
```
나머지 로직은 HEAD와 origin/develop이 완전히 동일했다.

**HEAD 버전을 채택한 이유**: 이미 병합 확정된 `RiskScore` 엔티티가
`is_representative` NOT NULL 컬럼을 갖고 있었다(같은 `feature/backend-db-schema` 브랜치에서
추가된 스키마). 이 값을 채우지 않으면 항목별/기업대표 점수 구분이 깨지므로, develop 쪽의
누락은 의도된 로직 차이가 아니라 스키마 반영 이전 시점 코드였을 뿐이라고 판단해 HEAD 그대로
채택했다.

병합 후 `compileJava compileTestJava`, `test` 모두 통과 확인.

커밋: `9dddd50` — `Merge remote-tracking branch 'origin/develop' into feature/backend-db-schema`

---

## 3. 크롤링(태스크 1-3) 실제 검증 결과

`sprint02_완료보고.md`에 "실제 네트워크 크롤링 검증 미수행"으로 남아있던 항목을 로컬 MySQL의
실제 기업 5개(카카오/네이버/배달의민족/토스/당근마켓) 데이터로 검증했다.

**1차 실크롤링** (`CompanyPolicyCrawlService.crawlAll()`, 임시 `@SpringBootTest`로 실행):
5개 중 **네이버 1건만 성공**. 나머지 4건은 DB에 저장된 `privacy_url`이 오래됐거나 틀려서
카카오는 DNS 조회 자체가 실패(`UnknownHostException`), 배민/토스/당근마켓은 HTTP 404였다.

**URL 갱신**: 웹 검색 + `curl`로 정적 HTML에 실제 텍스트가 있는지 검증한 뒤 SQL UPDATE 제안 →
승인 후 실행.
- 카카오: `https://policy.kakao.com/ko/privacy/policy` → `https://www.kakao.com/ko/privacy`
- 토스: `https://www.toss.im/policy/privacy` → `https://toss.im/privacy-policy`
- 당근마켓: `https://www.daangn.com/wv/terms/privacy` → `https://privacy-policy.daangn.com/`
- 배달의민족: **변경하지 않음**. `www.baemin.com`/`terms.baemin.com`이 전부 React/Next.js SPA라
  정적으로 크롤링 가능한 URL을 찾지 못했고, `Company.privacyUrl`이 `nullable=false`라
  NULL로 비울 수도 없어 기존 값을 그대로 유지.

**2차 재검증**: 카카오(15,131자)/네이버(17,723자)/당근마켓(21,715자) **3건 성공**, 배달의민족은
여전히 HTTP 404로 실패. **토스는 배치 결과상 "성공"으로 집계됐지만, 실제 저장된 `raw_text`가
zero-width 문자 35자뿐인 사실상 빈 텍스트였다** — 토스도 Next.js SPA라 실제 정책 텍스트가
`<script id="__NEXT_DATA__">` JSON 안에만 있고 화면에 정적 렌더링되지 않았기 때문.

이 두 건(배달의민족/토스)의 원인과 임시 조치, 향후 검토사항은
[docs/known_issues.md](./known_issues.md)에 별도로 기록했다.

**부수적으로 발견한 silent failure 버그와 수정**: `CompanyPolicyCrawlService`는 예외만
안 나면 무조건 성공으로 집계하므로, 토스처럼 "HTTP 200은 오는데 본문이 사실상 비어있는" 케이스를
걸러내지 못하고 있었다. `PolicyBodyCrawler.fetchCleanText()`에 zero-width 문자(U+200B/200C/200D/FEFF)를
제거한 뒤 실질 텍스트 길이가 **100자 미만이면 `PolicyCrawlException`을 던지도록** 수정해,
이런 silent failure를 크롤링 실패로 정확히 잡아내도록 했다. 단위 테스트 5건 추가
(빈 본문 / zero-width 문자만 있는 본문 / 100자 미만 짧은 본문 / 정상 길이 통과, 총
`PolicyBodyCrawlerTest` 7건 전부 통과). 이 수정 이후 토스 URL로 재검증을 다시 돌리지는
않았지만, 앞으로 이 코드 경로를 타면 토스는 (silent 성공이 아니라) 정상적으로 실패로
잡히게 된다.

커밋:
- `eaef431` — `docs: 배달의민족/토스 크롤링 불가 이슈 기록`
- `ec59179` — `fix: 크롤링 성공 판정에 raw_text 최소 길이 검증 추가 (silent failure 방지)`

---

## 4. ConsentItem 배치 insert 실 DB 검증

`sprint02_완료보고.md`에 "Mockito로는 `save()` 미호출까지만 검증, 실 DB 트랜잭션 롤백은
통합 테스트로 별도 확인 필요"로 남아있던 항목을, `@SpringBootTest` 기반 실 DB(로컬 MySQL)
통합 테스트로 검증했다.

`ConsentItemBatchServiceIntegrationTest` 신설 — 테스트 전용 Company를
`@BeforeEach`에서 만들고 `@AfterEach`에서 하위 ConsentItem과 함께 정리해 기존 데이터에
영향을 주지 않도록 구성. 4가지 시나리오를 실제 MySQL 위에서 검증했다.

| 시나리오 | 결과 | 실제 DB count |
|---|---|---|
| 정상 3건 저장 | 통과 | 3 (Hibernate SQL 로그로 INSERT 3건 실제 실행 확인) |
| 마지막 1건 점수범위(0~10) 위반 → 전체 롤백 | 통과 | 0 |
| 존재하지 않는 companyId → 롤백 | 통과 | 0 |
| **DB 제약(NOT NULL) 위반으로 부분 insert 후 실제 롤백** | 통과 | 0 |

**참고**: 두 번째·세 번째 시나리오는 SQL 로그로 재확인한 결과, `ConsentItemBatchService.saveAll()`이
`items.forEach(this::validateScoreRange)`로 **리스트 전체를 먼저 검증한 뒤에야** 엔티티를
만들어 저장하는 구조라서 실제로는 INSERT 시도 자체가 한 건도 없었다(즉 "이미 쓴 걸 되돌린 것"이
아니라 "애초에 안 썼다"). 최종 결과(0건)는 요구사항과 일치하지만, `@Transactional` 롤백
메커니즘 자체를 실제로 검증한 것은 아니라는 점을 확인했다.

이 한계를 보완하기 위해 **네 번째 시나리오를 추가로 설계**했다: 3번째 `ConsentItemDto`의
`itemName`을 `null`로 줘서 애플리케이션 레벨 검증(`validateScoreRange`는 점수만 확인)은
통과시키되, `ConsentItem.itemName`의 NOT NULL 제약을 실제 INSERT 시점에 위반하도록 구성했다.
`ConsentItem`이 `GenerationType.IDENTITY`라 `saveAll()` 안에서 항목마다 즉시 INSERT가
나가는 특성을 이용한 것으로, Hibernate SQL 로그로 다음을 직접 확인했다.

1. 1번째 항목 `insert into consent_item ...` — **실제 실행됨**
2. 2번째 항목 `insert into consent_item ...` — **실제 실행됨**
3. 3번째 항목에서 `DataIntegrityViolationException` 발생 (Hibernate flush 단계에서 NOT NULL 위반 감지)
4. 트랜잭션 롤백 → `count=0` — **1·2번째가 이미 실제 INSERT된 뒤에 진짜로 롤백됨**을 증명

지원용으로 `CompanyRepository.findByPackageName`, `ConsentItemRepository.countByCompany_CompanyId`
/ `findByCompany_CompanyId`를 추가했다 (커스텀 `deleteBy...` 파생 삭제 쿼리는 자동으로
트랜잭션이 안 걸려 `TransactionRequiredException`이 나는 걸 확인하고, 기본 CRUD
`deleteAll(Iterable)`을 쓰는 방식으로 우회).

커밋:
- `51c209a` — `test: ConsentItem 배치 insert 트랜잭션 롤백 실 DB 통합 테스트 추가`
- `914ddf1` — `test: ConsentItem 배치 insert 부분 커밋 후 롤백 시나리오 검증 추가`

---

## 5. 최종 커밋 목록

이번 세션에서 `feature/backend-db-schema`에 반영되어 `origin`으로 push된 커밋 (시간순):

| 커밋 | 메시지 |
|---|---|
| `9dddd50` | Merge remote-tracking branch 'origin/develop' into feature/backend-db-schema |
| `a57a3d8` | chore: 중복 클론 폴더(dynamic-consent-system) 및 죽은 예전 스캐폴딩 제거 |
| `ec59179` | fix: 크롤링 성공 판정에 raw_text 최소 길이 검증 추가 (silent failure 방지) |
| `eaef431` | docs: 배달의민족/토스 크롤링 불가 이슈 기록 |
| `51c209a` | test: ConsentItem 배치 insert 트랜잭션 롤백 실 DB 통합 테스트 추가 |
| `914ddf1` | test: ConsentItem 배치 insert 부분 커밋 후 롤백 시나리오 검증 추가 |

---

## 6. Sprint 02 태스크별 최종 완료 상태

| 태스크 | 상태 | 비고 |
|---|---|---|
| 1-3 크롤링 | **3/5 성공** | 카카오/네이버/당근마켓은 실 네트워크로 검증 완료. 배달의민족/토스 2건은 SPA(React/Next.js) 구조상 Jsoup으로 크롤링 불가한 구조적 한계로, `known_issues.md`에 원인과 향후 방향(헤드리스 브라우저 도입 등) 문서화 |
| 1-6 변경 감지 | 완료 | 변경/무변경/최초수집 3케이스 단위 테스트 통과 (`sprint02_완료보고.md` 태스크3과 동일, 변경 없음) |
| 1-7 스케줄러 연동 | 완료 | `runPipeline()` 수동 트리거 분리, 단위 테스트 2건 통과 (`sprint02_완료보고.md` 태스크4와 동일, 변경 없음) |
| DTO → DB 적재 (ConsentItem 배치 insert) | **완료 — 실 DB 롤백 검증까지 완료** | 정상 저장 / 점수범위 위반 롤백 / 존재하지 않는 companyId 롤백 / DB 제약위반 부분insert 후 실제 롤백, 4개 시나리오 모두 실 MySQL 통합 테스트로 확인 |
| is_representative 컬럼 | 완료 | develop 병합 충돌을 거쳐 최종 반영 확인 (HEAD 버전 채택), `RiskPipelineService`에서 항목별=false/대표=true 세팅 유지 확인 |

**남은 이슈**: 배달의민족·토스 실크롤링(SPA 렌더링 문제)은 이번 스프린트 범위에서 해결하지
않고 구조적 한계로 문서화만 완료. 헤드리스 브라우저 도입 여부는 별도 논의 필요
([known_issues.md](./known_issues.md) 참고).