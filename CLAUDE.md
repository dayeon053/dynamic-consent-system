# Dynamic Consent System (동의ON)

개인정보 처리 약관을 자동 분석해 위험도를 정량화하고, 사용자의 동의 철회를 돕는 Android 앱 + Spring Boot 백엔드.

## 개발 전 참고할 기획 문서

`docs/planning/`에 아래 4개 문서가 있다 (Notion 원본의 로컬 사본, git 미추적 — 팀원 클론 환경에는 없을 수 있음). 기능 구현·설계 판단을 내리기 전에 관련 문서를 먼저 열어서 확인한다.

- `docs/planning/유즈케이스_최종.md` — 기능 요구사항(F1~F6), 유즈케이스(UC-01~07), 제약사항. **새 기능을 만들거나 기존 동작을 바꿀 때 반드시 먼저 확인.**
- `docs/planning/유즈케이스_기반_태스크_목록.md` — Epic/태스크 단위 작업 목록과 완료 기준. 어떤 태스크 번호(예: 2-4, 4-7)에 해당하는 작업인지 먼저 찾아보고 완료 기준에 맞춰 구현한다.
- `docs/planning/개발_계획.md` — 스프린트별 담당자·진행 상황(체크박스). 지금이 몇 스프린트인지, 이 작업이 누구 담당인지, 이미 계획에 없는 새 작업인지 확인할 때 참고.
- `docs/planning/위험도.md` — 위험도 산출 공식(`Risk Score = DS + (ES × TF × PC × AI) × 2`)과 5대 변수·등급 경계값의 설계 근거. `common-model`의 `RiskCalculator`/`RiskGrade`를 건드릴 때 반드시 참고.

이 문서들이 로컬에 없으면(다른 팀원 환경 등) 무시하고 코드와 커밋 히스토리를 기준으로 판단한다.

## 저장소 구조

- `backend/consentradar/` — Spring Boot 백엔드 (크롤링, LLM 파싱, 위험도 산출 API)
- `common-model/` — 백엔드+프론트 공용 위험도 산출 로직 (`RiskCalculator`, `RiskGrade`, `RiskInput`, `RiskResult`)
- `frontend/` — Android 앱

## 알려진 이슈 / 미완성 상태

`docs/known_issues.md`, `docs/personal_risk_server_decision.md`도 함께 참고. `RiskPipelineService`(크롤링→LLM→위험도산출→DB저장)는 스케줄러(`PolicyCrawlScheduler.processCompany()`)에 이미 연결되어 있어, 최초 수집이거나 약관이 실제로 변경된 기업에 대해 매일 새벽 3시 자동 호출된다(2026-07-26 기준). 이 연결 때문에 `analyzeAndSaveRisk()`가 재분석 때마다 ConsentItem을 insert만 해 중복이 쌓이는 라이브 버그가 실제로 발생했고, PR #24에서 itemName 기준 upsert로 수정했다 — 관련 내용은 `docs/known_issues.md` 참고.
