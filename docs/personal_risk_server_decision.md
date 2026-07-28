# 개인 맞춤 위험도(2-2 / 2-5) 서버 구현 여부 검토

> 작성: 황다연(위험도) · 상태: **결정 완료 (2026-07-26) — 서버 구현(옵션 B) 확정, 정본 모델은 combineImpacts로 통일**
> 목적: 2-2/2-5 개인 맞춤 위험도 로직이 현재 프론트(Kotlin)에만 있는 상황에서,
> 서버 구현이 필요한지와 담당자를 팀이 확정하기 위한 근거 정리.

---

## 1. 배경

동의 토글에 따른 **개인 맞춤 위험도 재산출**(2-2)과 **동의 철회 효과 시뮬레이션**(2-5)
로직이 현재 프론트(`frontend/.../domain/`)에만 구현되어 있다. 서버에는 이에 대응하는
사용자별 재산출 API가 (develop 기준) 없다.

---

## 2. 현재 구현 현황

### 프론트 (Kotlin) — 개인 맞춤 로직 보유

| 로직 | 파일 | 설명 |
|---|---|---|
| 토글 반영 재산출 | `RiskRecalculator.recalculate()` | 사용자가 켠 항목만으로 점수·등급·분석 전체 재계산 |
| 변수 합성 | `RiskCalculator.combineImpacts()` | **동의 항목들의 변수별 최댓값**을 취해 하나의 변수묶음 생성 |
| 항목별 철회 효과 (2-5) | `RiskRecalculator` 내 `withdrawalEffects` | 항목 하나 끄면 몇 점 감소하는지 항목별 계산 |
| 최대 감소 효과 | `MaxEffect` | 전부 껐을 때 도달하는 최소 점수·감소폭 |

### common-model (Java) — 빌딩 블록 보유

| 함수 | 설명 |
|---|---|
| `RiskCalculator.calculate(input)` | 단일 항목 점수 산출 |
| `RiskCalculator.calculateMax(inputs)` | **항목별 점수를 각각 계산한 뒤 최고 점수** 반환 |
| `RiskCalculator.calculateRevocationEffect(a, b)` | 철회 전후 점수 차 |

### 서버 (Spring, develop 기준)

- 크롤링→LLM→산출→저장 파이프라인(`RiskPipelineService`)과 **기업 대표 위험도** 저장은 있음.
- **사용자별 토글 재산출 API는 이미 구현되어 있음** (`PATCH /users/{userId}/consents/{consentItemId}`,
  `ConsentApiController`/`ConsentApiService.toggleConsent()`, develop 병합 완료). 필수동의 전체 +
  사용자가 실제 체크한 선택동의 기준으로 재산출한다(`calculatePersonalRisk`). 대표값 합성 방식은
  `combineImpacts`(변수별 최댓값 합성)로 전환됨 — 5번 항목 참고.

---

## 3. ⚠️ 핵심 리스크 — FE와 서버의 산출 모델이 다름

프론트와 common-model이 "여러 동의 항목 → 대표 위험도"를 **서로 다른 방식**으로 계산한다.

| | 프론트 `combineImpacts` | common-model `calculateMax` |
|---|---|---|
| 방식 | **변수별 max**를 먼저 합성 → 그 묶음으로 점수 1회 산출 | **항목별 점수**를 각각 산출 → 점수의 max |
| 예시 | 항목A(ES=3,TF=1), 항목B(ES=1,TF=3) → 합성(ES=3,TF=3)로 계산 | A점수 vs B점수 중 큰 값 |
| 결과 | 두 방식의 점수가 **일치하지 않을 수 있음** | 〃 |

→ 서버가 개인 맞춤 위험도를 구현할 때 **어느 모델을 정본(canonical)으로 삼을지 먼저 합의**하지
않으면, 오프라인(FE 계산)과 온라인(서버 계산)의 숫자가 어긋난다.

---

## 4. 서버 구현 필요성 — 옵션 비교

### 옵션 A. 프론트 전용 유지 (서버 미구현)
- **장점**: 추가 서버 작업 없음. 토글이 즉각 반영(네트워크 왕복 불필요). 개인 동의상태를 서버에 안 보내도 됨(프라이버시).
- **단점**: 기기 간 동기화 불가. 산출식 변경 시 FE/서버 이중 관리 지속. 서버는 개인화 점수를 알 수 없어 알림·통계 등 확장 불가.

### 옵션 B. 서버 구현 (개인 맞춤 재산출 API)
- **장점**: 산출 로직 단일화(common-model 재사용). 기기 간 동기화·이력·알림 확장 가능. FE는 표시에 집중.
- **단점**: `combineImpacts`에 해당하는 로직을 서버에 추가 구현 필요. 개인 동의상태 저장 → 프라이버시/스키마 영향. PATCH 재산출 엔드포인트 정식화 필요(PR #18 로직 develop 승격).

---

## 5. 권고 (검토안)

1. **정본 모델 합의 먼저**: "여러 항목 → 대표 위험도"를 `combineImpacts`(변수별 max) 방식으로
   통일할 것을 권고. (개인 맞춤의 취지 = 사용자가 노출하는 변수 조합의 총합 위험이므로 변수별 max가 더 타당)
   → common-model의 `calculateMax`와 별개로 `combineImpacts` 대응 함수를 common-model에 추가하면
   FE/서버가 같은 코드를 공유할 수 있음.
2. **서버 구현은 옵션 B 방향 권고** (단, 기기간 동기화/알림 요구가 있을 때). 요구가 없다면 옵션 A 유지도 합리적.
3. **PATCH 토글 재산출 엔드포인트**(PR #18)를 develop로 승격하고, 위 정본 모델로 정렬.

---

## 6. 팀 결정 체크리스트 (회의에서 채움)

- [x] "여러 항목 → 대표 위험도" 정본 모델: combineImpacts (2026-07-26 팀 결정, 위험도 산정 모델 설계 문서의 카카오톡 분석 예시 근거)
- [ ] 개인 맞춤 위험도 서버 구현: ☐ 함 (옵션 B) ☐ 안 함 (옵션 A 유지)
- [ ] (구현 시) `combineImpacts` 대응 로직 common-model 이식 담당: ______
- [ ] (구현 시) 재산출 API/스키마(개인 동의상태 저장) 담당: ______ (백엔드)
- [ ] (구현 시) FE ↔ 서버 결과 일치 검증 테스트 담당: ______

---

## 참고 파일
- 프론트: `frontend/app/src/main/java/com/dynamicconsent/domain/RiskRecalculator.kt`, `RiskCalculator.kt`
- 공용: `common-model/src/main/java/com/dynamicconsent/algorithm/RiskCalculator.java`
- 서버 파이프라인: `backend/consentradar/src/main/java/com/consentradar/consentradar/pipeline/RiskPipelineService.java`
