# Stage 3 진행 기록 2 — Granularity 이슈 해소 및 데모 러너 자동실행 방지

작업일: 2026-07-13
브랜치: `feature/stage3-integration`
이전 문서: [stage3_progress.md](./stage3_progress.md) (커밋 `ef0b561`)

## 1. 이전 문서 이후 논의된 미해결 이슈

### 이슈 1 — RiskScore granularity 불일치
`RiskPipelineService.runWithCrawledPolicy()`가 **동의 항목(item) 단위로만**
`RiskScore`를 저장하고 있었음. 즉 카카오처럼 동의 항목이 2개면 `RiskScore`도 2건 저장됨.

- 문제: 프론트/API 명세는 "기업 1개당 대표 위험도 1건"을 기대하는데, 항목별 점수만 있으면
  어떤 걸 대표값으로 노출해야 하는지 알 수 없음.
- `common-model`의 `RiskCalculator`에는 이미 `calculateMax(List<RiskInput>)`가
  구현되어 있었지만(항목들 중 최고 점수를 기업 대표값으로 산출), 파이프라인에 연결되어
  있지 않아 실제로 호출되지 않고 있었음.

### 이슈 2 — KakaoPipelineDemoRunner 자동 실행 문제
`KakaoPipelineDemoRunner`가 `@Component`로 등록된 `CommandLineRunner`라서
앱을 기동할 때마다(로컬 개발용 `bootRun`뿐 아니라 향후 다른 목적의 기동에서도) 무조건
같이 실행됨.

- `application.yml`의 `spring.jpa.hibernate.ddl-auto: create` 설정과 맞물려,
  앱을 띄울 때마다 테이블이 drop & re-create 되고 그 위에 데모 러너가 목업 데이터를
  자동으로 밀어넣는 상태였음.
- 검증용으로 만든 임시 러너였는데 프로파일이나 플래그로 분리되어 있지 않아,
  이후 다른 목적으로 앱을 켤 때도 항상 같이 돌아간다는 점이 문제로 지적됨.

## 2. 처리한 작업

두 이슈 모두 이번 세션에서 바로 해결함 (아직 커밋 전, 워킹 트리 상태).

- [x] **`KakaoPipelineDemoRunner`의 `@Component` 주석 처리**
  자동 실행을 막되, 필요할 때 그 한 줄만 주석 해제하면 다시 수동으로 켤 수 있게 남겨둠.
- [x] **`RiskPipelineService`에 `RiskCalculator.calculateMax()` 연결**
  기존 항목별 `RiskScore` 저장 로직은 그대로 두고, 5단계(항목별 위험도 산출) 뒤에
  6단계로 "기업 대표 위험도 산출"을 추가함:
  - 루프 중 수집한 `RiskInput` 목록으로 `calculateMax()` 호출
  - 결과를 `user=null`, `company=해당 기업`인 `RiskScore` 1건으로 추가 저장
  - 반환되는 `List<RiskScore>`에도 포함시켜, 파이프라인 실행 1회당
    "항목별 N건 + 기업 대표 1건" 형태로 총 N+1건이 저장/반환됨

### 검증
러너를 임시로 다시 켜서 `bootRun`으로 직접 실행해 확인:
```
[Pipeline] 항목 RiskScore 저장 완료: 2건
[Pipeline] 6단계: 기업 대표 위험도 산출 시작
[Pipeline]   기업 대표 점수:  43.5 | 등급: Very High Risk(매우 위험)
[Pipeline] 완료. 저장된 RiskScore 총 3건 (항목별 + 기업 대표 1건)
```
`risk_score` 테이블에 3번째 행(43.5, VERY_HIGH)이 정상 저장되는 것을 확인한 뒤,
`@Component`를 다시 주석 처리해 자동 실행 안 되는 상태로 되돌림.

## 3. 남아있는 설계상 갭 (참고)
`RiskScore` 엔티티에는 항목별 점수인지 기업 대표 점수인지 구분할 수 있는 필드가 없음
(둘 다 `company_id`만 갖고 있고, 특정 `ConsentItem`이나 "대표 여부" 플래그와 연결되어
있지 않음). 지금은 같은 기업에 대해 여러 `RiskScore` 행이 섞여 있어, 조회 시
"가장 최근에 저장된 것 중 total_score가 가장 큰 것"처럼 암묵적인 규칙으로 대표값을
추론해야 함. API/프론트에서 대표값을 명확히 조회하려면 스키마 보완이 필요할 수 있음.

## 다음 세션에서 이어서 할 일
- [ ] 위 "설계상 갭" 해결 여부 결정: `RiskScore`에 `isRepresentative` 같은 플래그를
      추가할지, 아니면 조회 시 규칙(예: 최신 + 최고점)으로 처리할지 논의
- [ ] 이번 세션 변경분(`RiskPipelineService` 6단계 추가, `KakaoPipelineDemoRunner`
      `@Component` 주석 처리) 커밋
- [ ] LLM 실제 API 키 발급 후 `LlmClient` 목업 응답을 실제 호출로 교체
- [ ] 카카오 외 다른 기업(`CrawlTarget`)에 대해서도 동일 파이프라인 검증
- [ ] 중복된 프로젝트 디렉터리 정리 여부 검토 (`consentradar/consentradar/src`,
      `dynamic-consent-system` 등 미사용 사본 정리) — `stage3_progress.md`에서
      이미 제기된 항목, 아직 미처리