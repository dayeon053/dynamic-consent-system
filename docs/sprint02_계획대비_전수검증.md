# Sprint 02 계획 대비 전수 검증

> ⚠️ **이 문서는 Sprint 03 시작 전 팀 논의가 필요한 구조적 이슈를 포함합니다.**

대상 프로젝트: `backend/consentradar` (+ `common-model`)
검증 방법: 계획 원문의 각 태스크 완료 기준을 코드를 직접 열어 대조, 이번 세션(및 직전
세션)에서 실제 DB/네트워크로 검증한 내용은 그대로 반영
관련 문서: [sprint02_완료보고.md](./sprint02_완료보고.md), [sprint02_최종검증보고.md](./sprint02_최종검증보고.md), [known_issues.md](./known_issues.md)

핵심 요약: LLM 호출이 완전히 하드코딩된 mock이고, `RiskPipelineService`가 쓰는 크롤러(`PolicyCrawler`)가
실제로 검증·수정된 크롤러(`PolicyBodyCrawler`)와는 다른 별개 구현체이며, 전체 파이프라인의 실제
진입점(`RiskPipelineService.run()`)은 어디서도 호출되지 않는 죽은 코드다.

---

## Epic 1. 크롤링 봇

| 태스크 | 계획상 완료 기준 | 실제 구현 여부 | 실제 검증 여부 | 비고 |
|---|---|---|---|---|
| 1-1 크롤링 대상 기업 선정 및 URL 목록 | Company 테이블 등록 + URL 목록 문서화 | **부분 구현** — DB에 실제 5개 기업(카카오/네이버/배달의민족/토스/당근마켓) 등록 확인 | 구현됨(DB) / **문서화는 안 됨** | `README.md`/`docs/` 어디에도 "선정 사유 + URL 목록" 형태의 전용 문서 없음. 코드상 `CrawlTarget`엔 카카오 1개만 하드코딩. URL 목록은 사실상 DB row가 유일한 출처 |
| 1-2 크롤링 봇 기본 구조 설계 (수집→LLM파싱→변수매핑→위험도산출→DB저장→변경감지) | 전체 파이프라인이 코드상 연결되어 흐름 | **구현됨** — `RiskPipelineService`가 크롤링→`LlmPromptTemplate`→`LlmRetryModule`+`LlmClient`→`RiskCalculator`→`ConsentItem`/`RiskScore` 저장까지 순서대로 명확히 연결 | **검증 안 됨(사실상 죽은 코드)** | 이 파이프라인의 실제 크롤링 진입점 `RiskPipelineService.run()`(→`PolicyCrawler.crawl()`)을 호출하는 코드가 **레포 전체에 하나도 없음** — `PolicyCrawlerService.crawlAndAnalyze()`가 유일한 호출자인데 이것도 아무도 안 씀(고아 코드). 실제로 한 번이라도 실행된 건 `runWithCrawledPolicy()`를 mock 텍스트로 직접 호출한 `KakaoPipelineDemoRunner`뿐이고, 그마저 지금 `@Component` 주석 처리로 비활성화됨 |
| 1-3 개인정보처리방침 웹 크롤링 구현 | `PolicyBodyCrawler`/`CompanyPolicyCrawlService` 구현, "5개 기업 텍스트 수집 성공" | **구현됨** | **부분 검증 — 3/5 성공** | 실네트워크로 검증: 카카오/네이버/당근마켓 성공, 배달의민족/토스는 SPA(React/Next.js) 구조상 구조적으로 불가(`known_issues.md`). 단, **`RiskPipelineService`가 쓰는 크롤러는 이 `PolicyBodyCrawler`가 아니라 별개의 `PolicyCrawler`**(재시도 없음, 200자 임계값의 독자적인 길이체크 보유) — 두 크롤러가 중복 구현으로 분기되어 있음 |
| 1-4 LLM 파싱 모듈 구현 | `LlmClient`/`LlmResponseParser` 구현, "1개 기업 이상 필수/선택동의 항목 정상 파싱" | **`LlmResponseParser`/`LlmRetryModule`/`LlmPromptTemplate`은 견고하게 구현됨**. **`LlmClient`는 실제 LLM 미연동, 완전히 하드코딩된 mock** (`// TODO: API 키 생기면 실제 LLM 호출로 교체`, 입력 프롬프트를 무시하고 항상 "카카오" 고정 JSON 반환) | **자동화 검증 없음** | `common-model`에 `src/test` 디렉터리 자체가 없어 `LlmResponseParser`/`RiskCalculator` 등 전부 **단위테스트 0개**. "1개 기업 파싱 확인"은 `KakaoPipelineDemoRunner` 1회 수동 실행(mock↔mock)이 유일한 근거이고, 그마저 지금 비활성화 상태 |
| 1-5 5대 변수(DS/ES/TF/PC/AI) 자동 매핑 로직 | 실제 매핑 코드, "5개 기업 전체 변수 매핑 결과 확인" | **구현됨** — `ConsentItemAnalysis.toRiskInput()`이 LLM enum 문자열→`DataSensitivity`/`ExposureScope`/`TimeFactor`/`PurposeClarity`/`AiRiskFactor` enum으로 정확히 매핑 | **검증 안 됨** | LLM이 mock이라 "5개 기업 전체"는커녕 1개 기업도 실데이터 기반으로 매핑된 적 없음(항상 하드코딩된 카카오 mock 값만 매핑됨) |
| 1-6 전일 대비 약관 변경 감지 로직 | `PolicyChangeDetectionService`, SHA-256 비교 | **구현됨** | **검증됨** | `PolicyTextHasher`+`PolicyChangeDetectionService`, SHA-256 비교로 변경/무변경/최초수집 3케이스 단위 테스트 3건 전부 통과 (mock repository 기반, 로직 자체는 검증 완료) |
| 1-7 Spring Boot 스케줄러 연동 | `@Scheduled` 적용, "스케줄러 실행 로그 확인, 수동 개입 없이 자동 동작 확인" | **구현됨** — `@Scheduled(cron="0 0 3 * * *")` + `runPipeline()` 분리 | **부분 검증** | `runPipeline()`은 mock 기반 단위테스트 2건으로 집계 로직만 검증. **`scheduledRun()`(실제 cron 트리거) 자체는 의도적으로 테스트 대상에서 제외**됐고(sprint02_완료보고.md에 명시), 실제로 새벽 3시에 자동 실행되는지, 로그가 실제로 남는지는 한 번도 실증되지 않음 |
| 1-9 크롤링 대상 기업 관리 기능(관리자) | 우선순위 "중" | **미구현** | 해당 없음 | 레포 전체에 `@RestController`/`@Controller` 자체가 0개 — API 엔드포인트가 전혀 없는 상태. 우선순위상 아직 안 해도 정상 |

---

## Epic 2. 위험도 자동 산출 및 DB 저장

| 태스크 | 계획상 완료 기준 | 실제 구현 여부 | 실제 검증 여부 | 비고 |
|---|---|---|---|---|
| 2-1 위험도 산출 공식 (`DS + (ES×TF×PC×AI)×2`) | 공식이 코드와 정확히 일치 | **구현됨, 공식 정확히 일치** | 부분 검증 | `RiskCalculator.calculate()`에서 `double compoundFactor = es*tf*pc*ai; double rawScore = ds + compoundFactor*2;` — 계획 공식과 1:1 일치 확인. 다만 `common-model`에 테스트가 아예 없어서(위 1-4 참고) 이 계산 자체를 검증하는 자동화 테스트는 없음 — 코드 리딩으로만 확인 |
| 2-2 개인 맞춤 위험도 산출 로직 (필수동의+사용자 체크 선택동의) | 필수+선택 반영 로직 | **미구현** | 해당 없음 | `UserConsentCheck` 엔티티는 존재하지만 `@Getter`만 있고 `@Setter`도 없음, **Repository 자체가 없음**, 이걸 읽어서 개인별 위험도를 계산하는 서비스가 백엔드에 전혀 없음. `RiskPipelineService`는 항상 `riskScore.setUser(null)`로 회사 단위 점수만 산출 |
| 2-3 위험도 5단계 등급 분류 | `RiskScore.grade` enum + 경계값 코드 존재, 경계값 정의 문서 | **완전히 구현됨** | 검증됨(간접) | `common-model`의 `RiskGrade` enum에 5단계 경계값(3.0/7.0/14.0/24.0/36.0/45.5)과 산정 근거(PIA/NIST/ISO 27005)까지 코드 주석으로 상세 문서화되어 있음. `RiskScore.Grade`(엔티티)와 이름 일치 확인. 다만 `RiskGrade.fromScore()` 자체의 경계값 단위테스트는 없음(1-4와 마찬가지로 common-model 테스트 부재) |
| 2-4 DB 날짜별 누적 저장 | (Sprint 03 예정) | **기초 구조만 있음** | 해당 없음(예정대로 정상) | `RiskScoreRepository`에 delete 관련 메서드/호출이 전혀 없어 `save()`만 계속 쌓이는 insert-only 구조라 자연스럽게 "삭제 없이 누적"은 되고 있음. 다만 같은 날 중복 실행 시 dedup 로직 등 날짜별 집계 설계는 아직 없음 — Sprint 03 범위라 현재 상태로 정상 |
| 2-5 선택동의 철회 시 위험도 실시간 반영 | 관련 로직 존재 | **백엔드 미구현** (프론트에만 존재) | 해당 없음 | `RiskCalculator.calculateRevocationEffect(before, after)` 메서드가 `common-model`에 정의는 되어 있으나 **백엔드 어디서도 호출되지 않는 죽은 코드**. 실시간 재계산은 `frontend/app/.../domain/RiskRecalculator.kt`에 프론트 로컬 로직으로만 구현되어 있고, 서버 DB와 연동된 반영은 없음 |

---

## 계획과 실제 구현 사이 괴리 요약

1. **LLM 연동이 완전히 mock** — `LlmClient.callWithPrompt()`가 입력을 무시하고 항상 카카오용 하드코딩 JSON을 반환합니다. 1-4/1-5의 "정상 파싱/변수 매핑 확인"이라는 완료 기준은 이 mock 위에서만 성립하고, 실제 LLM으로 검증된 적은 없습니다.
2. **파이프라인 전체(`RiskPipelineService.run()`)가 아무도 호출하지 않는 죽은 코드** — 구조상으로는 크롤링→LLM→위험도산출→DB저장까지 완벽히 연결돼 있지만, 실제 앱에서 이 경로를 타는 진입점(컨트롤러/스케줄러/러너)이 전혀 없습니다. 유일하게 실행됐던 `KakaoPipelineDemoRunner`도 지금 비활성화 상태입니다.
3. **크롤러가 중복 구현 상태** — `RiskPipelineService`(전체 파이프라인)는 재시도 없는 구식 `PolicyCrawler`를, `CompanyPolicyCrawlService`/`PolicyCrawlScheduler`(실제로 검증·수정된 쪽)는 재시도+silent failure 방지 로직이 있는 `PolicyBodyCrawler`를 씁니다. silent failure 버그 수정(`ec59179`)은 후자에만 적용됐고, 전체 파이프라인 쪽은 여전히 취약합니다.
4. **`common-model` 모듈 테스트 0개** — 위험도 산출 공식(2-1), 등급 분류(2-3), LLM 파싱(1-4)처럼 프로젝트의 핵심 계산 로직을 담은 모듈에 자동화 테스트가 전혀 없습니다. 코드 리딩으로 공식 일치는 확인했지만 회귀 방지 안전망이 없는 상태입니다.
5. **개인 맞춤 기능(2-2, 2-5)이 백엔드에 전무** — `UserConsentCheck` 엔티티만 스키마로 존재하고 Repository/Service가 없습니다. 관련 로직(`calculateRevocationEffect` 포함)은 프론트엔드 Kotlin 쪽에만 로컬로 구현돼 있어, 서버가 개인화된 위험도를 계산/저장하지 못합니다.
6. **관리자 API가 전무(1-9)** — 이건 계획상 우선순위 "중"이라 예정대로이지만, 컨트롤러 계층 자체가 프로젝트에 하나도 없다는 점은 향후 Epic 진행 시 염두에 둘 부분입니다.
