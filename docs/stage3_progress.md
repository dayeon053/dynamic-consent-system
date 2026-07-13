# Stage 3 통합 진행 기록 — 카카오 파이프라인 데모 실행

작업일: 2026-07-13
브랜치: `feature/stage3-integration`
커밋: `ef0b561` — "feat: 카카오 파이프라인 데모 러너 추가 및 관련 누락분 보완"

## 목표

카카오 1개 기업을 기준으로 **크롤링(목업) → 파싱 → 위험도 산출 → DB 저장**
전체 파이프라인을 실제로 실행해서 콘솔 로그로 눈으로 확인한다.

## 진행 내용

### 1. 프로젝트 구조 파악
- 저장소 내에 `consentradar/consentradar`(루트 src) / `consentradar/backend/consentradar` /
  `dynamic-consent-system` 등 중복된 디렉터리가 여러 개 존재.
- `backend/consentradar/settings.gradle`에 `common-model`을 상대 경로로 include하는 것을 확인하고,
  실제 활성 Gradle 프로젝트 루트는 **`consentradar/consentradar/backend/consentradar`** 임을 확정.

### 2. 기존 코드 확인
이미 이전 커밋(`c9c6dca`, `56a8bb5`)에서 크롤러~파싱~위험도산출~저장 파이프라인 골격이 구현되어 있었음:
- `PolicyCrawler` — Jsoup으로 실제 URL 크롤링
- `LlmClient` — 목업 응답(하드코딩 JSON) 반환 (API 키 발급 전까지 임시)
- `RiskPipelineService` — 크롤링 → PolicySnapshot 저장 → LLM 프롬프트 생성 → LLM 호출/파싱 →
  `RiskCalculator`로 위험도 산출 → `ConsentItem`/`RiskScore` 저장까지 5단계 콘솔 로그 포함
- `CrawlTarget.kakao()` 팩토리 메서드로 카카오 대상 이미 정의됨

### 3. 발견한 누락/보완 필요 사항
- `CompanyRepository`가 존재하지 않아 `Company` 조회/저장 불가 → 신규 추가
- `Company` 엔티티에 `@Setter`가 없어 필드 채울 방법이 없음 → 추가
- 실제 네트워크 크롤링(`PolicyCrawler.crawl`)을 타지 않고 목업 데이터로 파이프라인만
  검증할 방법이 없었음 → `RiskPipelineService`에 `runWithCrawledPolicy(target, company, crawledDto)`
  오버로드를 분리 추가 (기존 `run()`은 그대로 실제 크롤러 사용, 신규 메서드는 이미 크롤링된
  데이터를 받아 2~5단계만 수행)

### 4. 신규 작성 파일
- `repository/CompanyRepository.java`
- `pipeline/KakaoPipelineDemoRunner.java`
  - `CommandLineRunner` 구현체. 앱 기동 시 자동 실행됨(프로파일 제한 없음, `ddl-auto: create`라
    매 기동마다 테이블이 재생성되므로 무해함)
  - 목업 카카오 개인정보처리방침 텍스트로 `CrawledPolicyDto` 생성 (실제 `kakaocorp.com` 요청 없음)
  - 카카오 `Company`가 없으면 신규 생성
  - `RiskPipelineService.runWithCrawledPolicy(...)` 호출 → 결과를 콘솔에 요약 출력
  - 확인 후 삭제하거나 `@Component`를 주석 처리하면 비활성화 가능

### 5. 실행 검증
- 로컬 도커 컨테이너 `consentradar-mysql`(mysql:8.0, 기존에 만들어져 있던 것)을 기동
- `./gradlew bootRun`으로 앱을 실제로 구동
- 콘솔에서 파이프라인 1~5단계 로그를 순서대로 확인:
  ```
  [Pipeline] 5단계: 위험도 산출 시작
  [Pipeline]   항목: 서비스 이용을 위한 필수 개인정보 수집 | 점수: 17.0 | 등급: Medium Risk(보통)
  [Pipeline]   항목: 마케팅 정보 수신 동의            | 점수: 43.5 | 등급: Very High Risk(매우 위험)
  [Pipeline] 완료. 저장된 RiskScore: 2건
  ```
- MySQL에 직접 접속해 `company` / `policy_snapshot` / `consent_item`(2건) / `risk_score`(2건)에
  데이터가 정상 저장된 것을 확인 (한글도 `utf8mb4`로 정상 저장, mysql CLI 클라이언트 charset
  설정 문제로 터미널에 `?`로 보였을 뿐 실제 데이터는 정상)

### 6. 커밋에 함께 포함된 기존 미커밋 변경분
다음 변경들은 이번 세션 시작 전부터 로컬에 미커밋 상태로 남아 있었고, 새 파이프라인이
정상 컴파일/동작하기 위해 필수적이어서 같은 커밋에 함께 포함함:
- `build.gradle` — `org.jsoup:jsoup:1.17.2` 의존성 추가
- `PolicySnapshot` — `@Setter` 추가
- `ConsentItem.java`, `RiskScore.java` — 파일 선두 BOM 제거

## 다음에 이어서 할 일 (제안)
- `KakaoPipelineDemoRunner`를 계속 둘지, 확인 끝났으니 제거할지 결정
- LLM 실제 API 키 발급 후 `LlmClient` 목업 응답을 실제 호출로 교체
- 카카오 외 다른 기업(`CrawlTarget`)에 대해서도 동일 파이프라인 검증
- 중복된 프로젝트 디렉터리 정리 여부 검토 (`consentradar/consentradar/src`,
  `dynamic-consent-system` 등 미사용 사본 정리)