# 배포 전 체크리스트

로컬 개발용 기본값(placeholder)이 그대로 배포 환경에 올라가지 않도록, 배포 전 아래 항목을
**전부** 확인한다. 이 프로젝트는 아직 profile(local/prod) 분리를 쓰지 않고
`application.yml` 하나로 가는 구조라, 잘못된 기본값이 그대로 떠도 서버가 죽지 않고
**조용히 그대로 뜬다** — 사람이 직접 확인해야 한다.

## 1. 관리자(admin) 계정 — `SecurityConfig`

- [ ] `ADMIN_USERNAME` 환경변수를 `admin`이 아닌 값으로 설정했는가
- [ ] `ADMIN_PASSWORD` 환경변수를 `local-dev-only-CHANGE-ME`가 아닌, 충분히 강한 값으로 설정했는가
- 기본값 그대로 배포하면 `/admin/**`(기업 등록/삭제, 크롤링 수동 트리거 `POST /admin/crawl/{id}`, `force=true` 강제 재분석 포함) 전체가 공개된 고정 계정으로 뚫린다.
- 안 바꾸고 그대로 기동하면 `SecurityConfig`가 시작 시 WARN 로그(`admin.security.password가 로컬 개발용 기본값입니다...`)를 남기지만, **서버는 그대로 뜬다** — 로그를 반드시 확인할 것.

## 2. DB 접속 정보 — `application.yml: spring.datasource`

- [ ] `spring.datasource.username`/`password`가 여전히 `root`/`1234`(로컬 dev 기본값)인지 확인
- [ ] 프로덕션 DB 계정으로 교체했는가
- ⚠️ 이 값은 **환경변수 오버라이드가 아예 없이 하드코딩**되어 있다(`admin.security.*`, `llm.*`와 다름). 배포 전 이 파일을 직접 고치거나, `${DB_USERNAME:root}` / `${DB_PASSWORD:1234}` 형태로 먼저 바꿔서 환경변수로 주입 가능하게 만들어야 한다.

## 3. LLM(OpenAI) 연동 — `application.yml: llm`

- [ ] `LLM_ENABLED=true`로 설정했는가 (기본값 `false` — 안 켜면 `LlmClient`가 항상 고정 mock 응답을 반환한다. 2026-08-26에 이 mock 폴백이 실제 서비스처럼 보이는 데이터를 만들어내 companyId 2~5의 위험도가 전부 동일하게 나오는 문제가 실제로 있었다)
- [ ] `OPENAI_API_KEY`를 실제 유효한 키로 설정했는가 (기본값은 빈 문자열 — 비어있으면 `LLM_ENABLED=true`여도 mock으로 폴백된다)
- [ ] `OPENAI_MODEL`, `OPENAI_BASE_URL` 기본값(`gpt-4o-mini`, OpenAI 공식 엔드포인트)이 실제 쓰려는 값과 맞는지 확인
- [ ] 키를 절대 `application.yml`에 직접 쓰지 말 것 — 반드시 환경변수/시크릿 매니저로만 주입

## 4. 시크릿 유출 확인

- [ ] `.env`, `secrets.properties`, `keystore.properties`, `google-services.json` 등이 `.gitignore`에 걸려있고 실제로 git에 커밋된 적 없는지 확인 (2026-08-26 기준 `git log --all` 전체 스캔 결과 없음 확인됨)
- [ ] 실제 API 키/비밀번호가 커밋 이력에 평문으로 남은 적 없는지 확인 (2026-08-26 기준 확인됨 — 아래 "보안 점검 이력" 참고)

## 5. 기타

- [ ] `ddl-auto: update`로 운영 중이라, 스키마 변경(`backend/consentradar/src/main/resources/sql/migration/V*.sql`)이 실제 운영 DB에도 반영됐는지 수동으로 확인 (Flyway/Liquibase 미사용 — 자동 적용 안 됨)
- [ ] PoC 단계 인증(`SecurityConfig`의 고정 in-memory 관리자 1계정)이 실제 서비스 규모에 맞는지 재검토 — User 테이블 기반 로그인으로 교체할 시점인지 판단

---

## 보안 점검 이력

### 2026-08-26 — 커밋 이력 시크릿 스캔
`git log --all -p` 전체를 `sk-`, `password=`, `api_key=`, `OPENAI_API_KEY=`, AWS 키, PEM
private key 패턴으로 스캔. 실제 시크릿 유출 없음을 확인했다.

- `sk-` 매치 24건 전부 오탐 — `feature/risk-*` 브랜치명/머지 커밋 메시지에 우연히 포함된
  부분 문자열("ri**sk-**calculator" 등)이며 실제 API 키 형식 아님
- `password: 1234` 2건 — `spring.datasource.password`의 로컬 개발용 placeholder. 실제
  운영 비밀번호 아님(위 2번 항목 참고)
- `api-key`/`apiKey` 매치는 전부 변수명 참조(`LlmClient.java`)뿐, `llm.api-key`는 코드
  전체 이력에서 항상 `${OPENAI_API_KEY:}`(빈 기본값)였고 실제 키가 하드코딩된 적 없음
- `ADMIN_PASSWORD` 기본값 이력: `admin1234!`(도입 커밋 `3073b70`) → `local-dev-only-CHANGE-ME`
  (변경 커밋 `437d497`, WARN 로그 추가와 함께). 둘 다 로컬 개발용 placeholder였고 실제
  운영 배포에 쓰인 적은 없는 것으로 확인됨(PoC 단계) — 다만 `admin1234!`는 git 이력에
  영구히 남아있으므로, 혹시 어딘가 이 값을 실제로 재사용한 적이 있다면 반드시 교체할 것
- AWS 액세스 키, PEM 형식 private key, JWT secret 패턴 매치 없음
- `.env`/`secrets.properties`/`keystore.properties`/`google-services.json` 등 git에 추가된
  적 없음

### 현재 상태 (2026-08-26 기준)
- `admin.security.password` 기본값은 여전히 `local-dev-only-CHANGE-ME` (placeholder 그대로,
  변경 안 됨 — 배포 전 위 1번 항목 반드시 처리)
