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
│   ├── AppConfig.kt             # USE_REMOTE_API 스위치, 백엔드 BASE_URL
│   ├── model/                   # 화면·API 공용 데이터 모델 (@Serializable)
│   │   ├── Organization.kt      #   기관 요약 (리스트 카드용, packageName 포함)
│   │   ├── OrganizationDetail.kt#   기업상세 전체 묶음
│   │   ├── ConsentDetail.kt     #   선택/필수 동의 목록 (+variableImpact)
│   │   ├── RiskAnalysis.kt      #   위험도 분석 (수식·변수·철회효과)
│   │   ├── RiskVariables.kt     #   5대 변수 값 묶음 (DS·ES·TF·PC·AI)
│   │   ├── RiskGrade.kt         #   5단계 등급
│   │   ├── ThirdPartyProvider.kt#   제3자 제공처
│   │   └── ConsentChangeRecord.kt#  동의 변경 기록 (세션 내 인메모리)
│   ├── remote/                  # 백엔드 REST 연동
│   │   ├── ConsentRadarApi.kt   #   Retrofit 인터페이스
│   │   ├── ApiClient.kt         #   Retrofit 인스턴스 생성
│   │   ├── CompanyMapper.kt     #   서버 응답 → 화면 모델 변환
│   │   └── dto/CompanyDtos.kt   #   응답 DTO
│   └── repository/
│       ├── OrganizationRepository.kt      # 데이터 소스 인터페이스
│       ├── DummyOrganizationRepository.kt # assets/mock JSON 파싱
│       ├── ApiOrganizationRepository.kt   # 실 API 구현체
│       ├── RepositoryProvider.kt          # AppConfig에 따라 둘 중 하나 주입
│       ├── ConsentStateStore.kt           # 화면 간 동의 상태 공유 (인메모리)
│       └── ConsentSyncManager.kt          # 토글 서버 전송 디바운스
├── domain/
│   ├── RiskCalculator.kt        # 위험도 수식·등급 분류 (common-model 이식)
│   └── RiskRecalculator.kt      # 동의 상태 반영 재산출
├── monitor/                     # (프론트 B) 앱 실행 감지 파이프라인
│   ├── AppLaunchDetector.kt     #   UsageStatsManager 폴링으로 포그라운드 진입 감지
│   ├── AppLaunchMonitorService.kt#  감지 루프를 도는 포그라운드 서비스
│   ├── WatchedAppRegistry.kt    #   감지 앱 패키지명 ↔ 기관 id 매핑 (서버 목록 기반)
│   ├── RiskOverlayPipeline.kt   #   감지 → 위험도 조회 → 오버레이 표시
│   ├── UsageAccessPermission.kt #   사용 정보 접근 권한
│   ├── BatteryOptimization.kt   #   배터리 최적화 예외 요청
│   ├── MonitorPreferences.kt    #   감시 on/off 상태 저장
│   └── BootReceiver.kt          #   재부팅 후 감시 자동 복구
├── overlay/                     # (프론트 B) 오버레이 팝업·권한·포그라운드 서비스
└── ui/
    ├── navigation/              # NavHost, 라우트, 딥링크
    ├── risk/                    # 위험기관리스트 화면
    ├── orgdetail/               # 기업상세 화면 (5개 탭)
    ├── monitor/                 # 감시 권한·시작·테스트 화면
    ├── common/                  # 공용 컴포넌트 (로고, 위험도 분석 섹션, 에러 재시도)
    └── theme/                   # 색상·타이포·등급별 색 매핑

frontend/app/src/main/assets/mock/   # mock JSON (USE_REMOTE_API=false일 때 데이터 소스)
frontend/app/src/test/               # 단위 테스트 (수식 경계값·재계산·매핑·API 파싱)
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

예) dynamicconsent://org/kakaotalk?tab=RISK   (mock 모드의 orgId)
    dynamicconsent://org/1?tab=RISK           (실 API 모드의 orgId = companyId)
    TAB: CONSENT | RISK | THIRD_PARTY | CHANGE_HISTORY | INFO (생략 시 CONSENT)
```

- `orgId`는 **데이터 소스가 내려준 기관 id를 그대로** 쓴다 (mock은 `"kakaotalk"`, 실 API는 `"1"` 같은 companyId).
- 감지한 앱 패키지명 → orgId 변환은 `WatchedAppRegistry.orgIdFor()` 사용.
  이 매핑은 하드코딩이 아니라 기관 목록의 `packageName` 필드로 런타임에 구성되므로,
  mock/실 API 어느 모드에서도 id 체계가 어긋나지 않는다.
- 코드에서 URI 생성: `Screen.OrgDetail.createDeepLinkUri(orgId, tab)`
- 에뮬레이터 테스트: `adb shell am start -a android.intent.action.VIEW -d "dynamicconsent://org/toss?tab=RISK"`

## 백엔드 API 연동

> **API 규약의 정본은 [`docs/api_spec.md`](../docs/api_spec.md)다.**
> 이 문서에는 명세를 옮겨 적지 않는다 (중복되면 한쪽이 반드시 낡는다).
> 프론트가 호출하는 엔드포인트는 아래 세 개이며, 요청·응답 형식은 정본을 따른다.
>
> - `GET /companies` — 기업 목록 (개인 맞춤 위험도 내림차순)
> - `GET /companies/{companyId}/consent-items` — 동의 항목 + 5대 변수 + 체크 상태
> - `PATCH /users/{userId}/consents/{consentItemId}` — 동의 토글 (요청 본문 없음)

### 데이터 소스 전환

`AppConfig.USE_REMOTE_API` 한 줄로 mock JSON ↔ 실 API를 전환한다.
`RepositoryProvider`가 이 값을 보고 `DummyOrganizationRepository` 또는
`ApiOrganizationRepository`를 주입하므로 **화면 코드는 변경할 필요가 없다.**

```kotlin
// data/AppConfig.kt
const val USE_REMOTE_API = false        // 현재 기본값: mock JSON
const val BASE_URL = "http://10.0.2.2:8080/"  // 에뮬레이터에서 본 호스트 PC의 localhost
```

기업 상세 화면은 `GET /companies`와 `GET /companies/{companyId}/consent-items`
**두 응답을 클라이언트에서 조합해** 구성한다 (통합 상세 엔드포인트는 백엔드에 없음).

위험도 점수·등급·철회 효과는 서버 값을 그대로 쓰지 않고,
동의 항목의 5대 변수로 **클라이언트에서 재산출**한다 — 스위치를 켜고 끌 때
서버 왕복 없이 즉시 반영하기 위해서다.

## 진행 현황

- [x] 프로젝트 세팅, MVVM 구조화, 화면 2종 기본 레이아웃 (#4)
- [x] mock JSON 설계 및 더미 데이터 전환 (#9)
- [x] 위험도 수식 이식 + 스위치 실시간 재계산·재정렬 (#10)
- [x] 딥링크 라우팅 (#13)
- [x] 기업상세 제3자 제공·동의 변경 내역 탭 (#15)
- [x] 위험도 탭 5대 변수 게이지 시각화 + 필수동의 고정 스위치 (#17)
- [x] 오버레이 팝업 (#8) + 백그라운드 앱 실행 감지 파이프라인 (#29, 프론트 B)
- [x] 실제 API 연동 코드 (Retrofit·DTO·매퍼·토글 동기화) (#28)
      — 단, 아래 "실서버 e2e 검증"은 아직 남아 있어 기본값은 mock(`USE_REMOTE_API = false`)이다
- [ ] 실서버 e2e 검증 — 백엔드가 MySQL을 필요로 해 로컬에서 서버를 띄우지 못했다.
      서버 배포 주소를 받거나 로컬 DB를 구성하면 플래그만 켜서 확인 가능
- [ ] 감시 대상 매핑 영구 캐시 — 앱 재시작 직후 매핑을 받기 전 첫 감지를 놓칠 수 있음.
      현재는 무시 후 최소 60초 간격 재시도로만 대응
- [ ] 공지사항 탭 — 백엔드 `GET /notices`가 아직 미구현 (`docs/api_spec.md` 2-5)
- [ ] FCM 푸시 알림 — 8월 스프린트 예정, 백엔드 알림 발송과 함께 진행
- [ ] 날짜별 위험도 추이 그래프 — `risk-history` API는 있으나 이력 적재 호출 시점이
      백엔드에서 미확정이라 조회해도 데이터가 비어 있을 수 있음 (`docs/api_spec.md` 2-4)
