# common-model

위험도 산출·LLM 응답 파싱 공용 모듈. 백엔드(`consentradar`)와 프론트(Android) 양쪽에서
동일한 계산 로직을 쓰기 위해 분리한 순수 Java 모듈이다. (Spring/JPA 의존성 없음)

- Java 23 · Gradle
- 외부 의존성: `jackson-databind`(파싱), 테스트: `junit-jupiter`
- 빌드/테스트: `./gradlew :common-model:test` (backend/consentradar의 gradlew 사용)

---

## 패키지 구조

```
com.dynamicconsent
├── model
│   ├── RiskInput            5대 변수 입력 컨테이너
│   ├── RiskResult           산출 결과(점수 + 등급) + DB 저장용 변환 헬퍼
│   ├── RiskGrade            5단계 등급 enum (점수 구간·라벨·권장조치)
│   └── variable             DS/ES/TF/PC/AI 5대 변수 enum
├── algorithm
│   └── RiskCalculator       위험도 산출 계산기 (2-1 공식)
└── llm
    ├── dto                  LlmRiskAnalysisResponse, ConsentItemAnalysis
    ├── parser               LlmResponseParser (1-4 LLM 응답 파싱)
    ├── prompt               LlmPromptTemplate
    ├── retry                LlmRetryModule
    └── exception            LlmParseException, LlmRetryExhaustedException
```

---

## 위험도 산출 공식 (2-1)

```
Risk Score = DS + (ES × TF × PC × AI) × 2
```

| 변수 | 의미 | 점수값 |
|---|---|---|
| DS | Data Sensitivity (민감도) | LOW=1 / MODERATE=3 / HIGH=5 |
| ES | Exposure Scope (노출범위) | LOW=1 / MEDIUM=2 / HIGH=3 |
| TF | Time Factor (보관기간) | SHORT=1 / MEDIUM=2 / LONG=3 |
| PC | Purpose Clarity (목적명확성) | COMPLIANT=1.0 / NON_COMPLIANT=1.5 |
| AI | AI Risk Factor (AI 위험계수) | LOW_RISK=1.0 / HIGH_RISK=1.5 |

점수 범위: **최솟값 3.0 ~ 최댓값 45.5** (소수점 1자리 반올림)

---

## 공개 함수 시그니처

### `algorithm.RiskCalculator` (static, 인스턴스화 불가)

```java
// 5대 변수 → 점수·등급 산출
static RiskResult calculate(RiskInput input)
    throws NullPointerException          // input 또는 내부 변수가 null

// 동의 항목 여러 개 → 대표(최고 점수) 위험도
static RiskResult calculateMax(List<RiskInput> inputs)
    throws IllegalArgumentException      // inputs 가 null 또는 빈 목록

// 동의 철회 효과 = 철회 전 점수 − 철회 후 점수 (양수 = 안전해짐)
static double calculateRevocationEffect(RiskInput original, RiskInput afterRevocation)
```

### `model.RiskGrade` (enum)

```java
static RiskGrade fromScore(double score)   // 점수 → 등급
    throws IllegalArgumentException          // score < 3.0 또는 > 45.5

// 필드
final double minScore, maxScore;             // 등급 점수 구간 [min, max)  (VERY_HIGH만 max 포함)
final String englishLabel, koreanLabel, recommendedAction;
```

등급 구간: `VERY_LOW[3.0,7.0)` · `LOW[7.0,14.0)` · `MEDIUM[14.0,24.0)` · `HIGH[24.0,36.0)` · `VERY_HIGH[36.0,45.5]`

### `model.RiskResult`

```java
double     getScore()               // 점수 (double, 소수점 1자리)
RiskGrade  getGrade()               // 등급 enum
BigDecimal getScoreAsBigDecimal()   // DB 저장용: scale 2 고정 (예: 45.5 → 45.50)
String     getGradeName()           // DB 저장용: 등급 상수명 문자열
```

### `model.RiskInput`

```java
RiskInput(DataSensitivity ds, ExposureScope es, TimeFactor tf,
          PurposeClarity pc, AiRiskFactor ai)
// getter: getDataSensitivity() / getExposureScope() / getTimeFactor()
//         / getPurposeClarity() / getAiRiskFactor()
```

### `llm.parser.LlmResponseParser` (static)

```java
static LlmRiskAnalysisResponse parse(String rawLlmOutput)
    throws LlmParseException  // 입력 null·공백, JSON 미발견, 역직렬화 실패,
                              // 필수 필드 누락, Enum 유효값 위반
// 처리: 마크다운/텍스트에서 JSON 추출 → 역직렬화 → Enum 대문자 정규화 → 검증
```

### `llm.dto.ConsentItemAnalysis`

```java
RiskInput toRiskInput()   // 파싱된 항목 → RiskCalculator 입력으로 변환
                          // (파서가 대문자 정규화한 뒤 호출해야 valueOf 성공)
```

---

## 백엔드 통합 가이드 (타입 불일치 · null 처리)

### 저장 흐름

```java
RiskInput  input  = analysis.toRiskInput();          // LLM 파싱 결과 → 입력
RiskResult result = RiskCalculator.calculate(input); // 산출

RiskScore entity = new RiskScore();
entity.setTotalScore(result.getScoreAsBigDecimal()); // double → BigDecimal (아래 주의)
entity.setGrade(RiskScore.Grade.valueOf(result.getGradeName())); // enum 매핑 (아래 주의)
```

### ⚠️ 타입 불일치 주의점

| 경계 | common-model | backend 엔티티 | 처리 |
|---|---|---|---|
| 점수 | `double` (`getScore()`) | `BigDecimal` (`totalScore`, precision 5 / scale 2) | **`getScoreAsBigDecimal()` 사용.** `new BigDecimal(double)`는 부동소수 오차를 담으므로 금지 |
| 등급 | `RiskGrade` (라벨·조치 포함) | `RiskScore.Grade` (상수만, **중복 정의**) | `Grade.valueOf(getGradeName())`. 두 enum 상수명이 1:1 일치해야 함 |

> **리팩터링 권고:** `RiskScore.Grade`는 `RiskGrade`와 상수가 완전히 중복된다. 향후 한쪽 등급이
> 바뀌면 `valueOf`가 런타임 예외를 던지므로, 백엔드가 별도 enum을 두지 말고 common-model의
> `RiskGrade`를 직접 참조하는 방향을 권장한다. (엔티티 변경은 백엔드 담당 영역이라 본 PR 범위 밖)

### null 처리 계약

- `RiskCalculator.calculate(null)` 또는 내부 변수 null → **`NullPointerException`** (명시적 메시지)
- `RiskCalculator.calculateMax(null | 빈 목록)` → **`IllegalArgumentException`**
- `RiskGrade.fromScore(범위 밖)` → **`IllegalArgumentException`**
- `LlmResponseParser.parse(...)` 모든 실패 → **`LlmParseException`** (RuntimeException)
- `RiskResult`의 `getScore()`/`getGrade()`는 항상 non-null (생성자에서 값 보장)

---

## 테스트

```bash
./gradlew :common-model:test
```

| 테스트 | 대상 | 케이스 |
|---|---|---|
| `RiskCalculatorTest` | 산출공식(2-1) | 11 |
| `RiskGradeBoundaryTest` | 등급 경계값(2-3) | 경계 10지점 |
| `RiskResultTest` | DB 저장 변환 헬퍼 | 3 |
| `LlmResponseParserTest` | LLM 파싱(1-4) | 13 |
