# Frontend (Android)

담당: 팀원2 (김도영), 팀원4 (이재은)

역동적 동의 체계 기반 개인정보 관리 서비스의 Android 클라이언트.
Jetpack Compose + MVVM으로 구현하며, 선택동의 스위치를 조작하면 위험도 점수가
클라이언트에서 즉시 재계산되고 위험기관리스트 순위가 실시간으로 재정렬된다.

## 작업 브랜치 규칙
- 프론트 기능: `feature/fe-기능명`

## 실행 방법
1. Android Studio에서 `frontend/` 폴더를 프로젝트로 연다
2. 에뮬레이터(API 26+) 또는 실기기 선택 후 Run ▶
3. 단위 테스트: `./gradlew testDebugUnitTest`

## 폴더 구조

```
frontend/app/src/main/java/com/dynamicconsent/
├── MainActivity.kt              # 진입점, 딥링크 onNewIntent 처리
├── data/
│   ├── model/                   # 화면·API 공용 데이터 모델 (@Serializable)
│   │   ├── Organization.kt      #   기관 요약 (리스트 카드용)
│   │   ├── OrganizationDetail.kt#   기업상세 전체 묶음
│   │   ├── ConsentDetail.kt     #   선택/필수 동의 목록 (+variableImpact)
│   │   ├── RiskAnalysis.kt      #   위험도 분석 (수식·변수·철회효과)
│   │   ├── RiskVariables.kt     #   5대 변수 값 묶음 (DS·ES·TF·PC·AI)
│   │   └── RiskGrade.kt         #   5단계 등급
│   ├── repository/
│   │   ├── OrganizationRepository.kt      # 데이터 소스 인터페이스
│   │   ├── DummyOrganizationRepository.kt # assets/mock JSON 파싱 (임시)
│   │   └── ConsentStateStore.kt           # 화면 간 동의 상태 공유 (인메모리)
│   └── OrgPackageMapping.kt     # 감지 앱 패키지명 ↔ 기관 id 매핑
├── domain/
│   ├── RiskCalculator.kt        # 위험도 수식·등급 분류 (common-model 이식)
│   └── RiskRecalculator.kt      # 동의 상태 반영 재산출
├── overlay/                     # (프론트 B) 오버레이 팝업·권한·포그라운드 서비스
└── ui/
    ├── navigation/              # NavHost, 라우트, 딥링크
    ├── risk/                    # 위험기관리스트 화면
    ├── orgdetail/               # 기업상세 화면 (5개 탭)
    ├── common/                  # 공용 컴포넌트 (로고, 위험도 분석 섹션)
    └── theme/                   # 색상·타이포·등급별 색 매핑

frontend/app/src/main/assets/mock/   # mock JSON (= 백엔드 API 응답 포맷 초안)
frontend/app/src/test/               # 단위 테스트 (수식 경계값·재계산·JSON 정합성)
```

## 아키텍처 흐름

```
mock JSON (assets)                    ┌─ 스위치 토글
   │ 파싱                             ▼
DummyOrganizationRepository ──► ViewModel ◄── ConsentStateStore (동의 상태 공유)
   (인터페이스 뒤에 숨김)            │ RiskRecalculator로 점수·등급 재산출
                                     ▼
                              Compose UI (리스트 재정렬·상세 갱신)
```

- **실시간 재계산**: 동의 항목마다 5대 변수 기여도(`variableImpact`)를 갖고,
  동의 중인 항목들의 변수 최댓값을 합성해 `RiskScore = DS + (ES × TF × PC × AI) × 2`로 계산한다.
  수식·등급 경계값(7/14/24/36)은 common-model 스펙과 동일하며, 변경 시 반드시 함께 수정한다.
- **화면 간 동기화**: 기업상세에서 스위치를 끄면 `ConsentStateStore`를 구독 중인
  위험기관리스트가 같은 상태를 보고 즉시 재정렬된다.

## 딥링크 (오버레이 팝업 연동)

오버레이 팝업의 "상세보기" 버튼 등 외부에서 기업상세 화면으로 진입할 때 사용한다.

```
dynamicconsent://org/{orgId}?tab={TAB}

예) dynamicconsent://org/kakaotalk?tab=RISK
    TAB: CONSENT | RISK | THIRD_PARTY | CHANGE_HISTORY | INFO (생략 시 CONSENT)
```

- 감지한 앱 패키지명 → orgId 변환은 `OrgPackageMapping.orgIdFor()` 사용
- 코드에서 URI 생성: `Screen.OrgDetail.createDeepLinkUri(orgId, tab)`
- 에뮬레이터 테스트: `adb shell am start -a android.intent.action.VIEW -d "dynamicconsent://org/toss?tab=RISK"`

## API 명세 초안 (백엔드 협의용)

`assets/mock/*.json`이 응답 포맷의 초안이다. 실제 서버 연동 시
`OrganizationRepository` 구현체만 Retrofit 기반으로 교체하면 화면 코드는 변경 없다.

| 메서드 | 엔드포인트 | 응답 포맷 | 용도 |
|--------|-----------|----------|------|
| GET | `/organizations` | `mock/organizations.json` | 위험기관리스트 (위험도 내림차순) |
| GET | `/organizations/{orgId}` | `mock/organization_details.json`의 항목 | 기업상세 (동의·위험도·기업정보) |
| PATCH | `/organizations/{orgId}/consents/{consentId}` | `{ "enabled": bool }` 요청 | 사용자 선택동의 체크 상태 저장 |

응답에 반드시 포함돼야 하는 것:
- 선택동의 항목별 `variableImpact` (5대 변수 기여도) — 클라이언트 실시간 재계산에 필요
- 기관별 `riskScore`·`riskGrade`는 "모든 선택동의 ON" 기준값 (변수 조합은 수식으로 검산 가능해야 함)

## 진행 현황

- [x] 프로젝트 세팅, MVVM 구조화, 화면 2종 기본 레이아웃 (#4)
- [x] mock JSON 설계 및 더미 데이터 전환 (#9)
- [x] 위험도 수식 이식 + 스위치 실시간 재계산·재정렬 (#10)
- [ ] 오버레이 팝업 + 백그라운드 앱 감지 (#8, 프론트 B)
- [ ] 딥링크 라우팅 (feature/fe-deeplink)
- [ ] 기업상세 제3자 제공·동의 변경 내역 탭
- [ ] 실제 API 연동 (Retrofit) 및 FCM 알림 — 8월 스프린트
