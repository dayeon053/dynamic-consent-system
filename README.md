# Dynamic Consent System

개인정보 처리 약관을 자동 분석하여 위험도를 정량화하고, 사용자의 동의 철회를 돕는 Android 앱.

---

## 팀 구성

| 역할 | 이름 | GitHub ID |
|------|------|-----------|
| 백엔드 | 노가현 | (GitHub ID 입력) |
| 위험도 산출 & BE 지원 | 황다연 | (GitHub ID 입력) |
| 프론트A | 김도영 | (GitHub ID 입력) |
| 프론트B | 이재은 | (GitHub ID 입력) |

---

## 📁 폴더 구조

```
dynamic-consent-system/
│
├── .github/
│   └── PULL_REQUEST_TEMPLATE.md   # PR 작성 양식
│
├── backend/                        # 팀원1(노가현) + 팀원3(황다연) 작업 공간
│   └── README.md
│
├── frontend/                       # 팀원2(김도영) + 팀원4(이재은) 작업 공간
│   └── README.md
│
├── common-model/                   # 백엔드 + 프론트 공용 데이터 모델 (팀원3 관리)
│   └── src/main/java/com/dynamicconsent/
│       ├── model/variable/         # 5대 변수 Enum 클래스
│       ├── model/                  # RiskGrade, RiskInput, RiskResult
│       └── algorithm/              # 위험도 산출 알고리즘
│
├── .gitignore
└── README.md                       # 이 파일
```

---

## 🌿 브랜치 전략

```
main          ← 최종 제출용. 절대 직접 push 금지.
  └── develop ← 기능 통합 테스트 공간. 직접 push 금지.
        ├── feature/be-api-spec         (팀원1)
        ├── feature/be-db-schema        (팀원1)
        ├── feature/risk-common-model   (팀원3)
        ├── feature/risk-pseudocode     (팀원3)
        ├── feature/fe-ui-layout        (팀원2)
        └── feature/fe-overlay-popup    (팀원4)
```

### 브랜치 이름 규칙

| 접두사 | 언제 쓰나 | 예시 |
|--------|----------|------|
| `feature/be-` | 백엔드 새 기능 | `feature/be-crawler` |
| `feature/fe-` | 프론트 새 기능 | `feature/fe-dashboard` |
| `feature/risk-` | 위험도 산출 모듈 | `feature/risk-calculator` |
| `fix/` | 버그 수정 | `fix/be-null-pointer` |

---

## ⚠️ 코드 올리기 전 필수 확인 (GitHub 처음 쓰는 분 주목!)

### 절대 하면 안 되는 것
- `main` 브랜치에 직접 `git push` ❌
- `develop` 브랜치에 직접 `git push` ❌
- `git push --force` (강제 push) ❌ → 남의 코드가 통째로 사라짐
- 다른 사람 브랜치에 허락 없이 push ❌

### 코드 올리는 올바른 순서 (매번 이 순서 지키기)

```bash
# 1. 작업 시작 전 — 항상 최신 코드 받아오기
git switch develop
git pull origin develop

# 2. 내 기능 브랜치 만들기 (브랜치 이름은 위 규칙 참고)
git switch -c feature/be-내기능이름

# 3. 코드 작성 후 저장
git add .
git commit -m "feat: 무엇을 했는지 한 줄로"

# 4. 내 브랜치 GitHub에 올리기
git push origin feature/be-내기능이름

# 5. GitHub 웹사이트에서 PR 열기 (아래 PR 규칙 참고)
```

---

## 📋 PR (Pull Request) 규칙 — 반드시 읽기

### PR이란?
> "내 코드를 `develop`에 합쳐도 되는지 팀원한테 검토 부탁하는 과정"
> PR 없이 직접 합치는 건 금지. 무조건 PR → 리뷰 → 승인 → Merge 순서.

### PR 여는 방법
1. GitHub 웹사이트 들어가기
2. 상단에 뜨는 노란 배너 **"Compare & pull request"** 클릭
   (안 뜨면 본인 브랜치 선택 후 **"Contribute"** → **"Open pull request"**)
3. **Base:** `develop`, **Compare:** 내 브랜치 확인
4. 양식 채우기 (아래 참고)
5. **Reviewers**에 파트 파트너 지정 후 제출

### PR 제목 규칙
```
[BE] 크롤러 기본 구조 구현
[FE] 위험기관 리스트 화면 레이아웃
[RISK] 5대 변수 Enum 클래스 작성
[FIX] 로그인 화면 null 오류 수정
```

### 리뷰어 배정 기준
- 백엔드 코드 → 팀원1 ↔ 팀원3 서로 리뷰
- 프론트 코드 → 팀원2 ↔ 팀원4 서로 리뷰
- 공용 모델(`common-model/`) → 전원 리뷰

### Merge 조건
- 리뷰어 **1명 이상** Approve ✅ 받아야 Merge 가능
- Merge는 PR 올린 본인이 직접 함 (리뷰어가 하지 않음)
- Merge 후 본인 브랜치 삭제 (GitHub에서 **"Delete branch"** 버튼 클릭)

---

## 💬 커밋 메시지 규칙

```
feat: 새 기능 추가
fix: 버그 수정
docs: 문서, 주석 수정
refactor: 기능 변화 없이 코드 구조 개선
test: 테스트 코드
chore: 빌드 설정, 패키지 변경
```

**예시**
```
feat: DS/ES/TF/PC/AI 5대 변수 Enum 클래스 작성
fix: 위험도 점수 소수점 반올림 오류 수정
docs: README 브랜치 전략 설명 추가
```

---

## 🚨 충돌(Conflict) 발생 시 대처법

충돌이란 두 사람이 같은 파일의 같은 줄을 동시에 수정했을 때 발생합니다.

```bash
# 충돌 발생 시
git pull origin develop     # 최신 develop 코드 가져오기
# → 충돌난 파일 열면 아래처럼 표시됨:
# <<<<<<< HEAD (내 코드)
# 내가 쓴 코드
# =======
# 남이 쓴 코드
# >>>>>>> develop

# 두 코드 중 남길 것만 정리한 뒤 저장하고:
git add .
git commit -m "fix: develop 브랜치와 충돌 해결"
git push origin feature/내브랜치
```

> **절대 당황하지 말 것.** 충돌은 흔한 일입니다. 모르면 팀원 부르기.

---

## 🛠️ 처음 세팅 방법 (클론부터 시작)

```bash
# 1. 내 컴퓨터에 저장소 가져오기
git clone https://github.com/dynamic-consent-system/dynamic-consent-system.git

# 2. 폴더 들어가기
cd dynamic-consent-system

# 3. develop 브랜치로 이동
git switch develop

# 4. 이제 위 '코드 올리는 올바른 순서' 따라 개발 시작
```

---

## 📅 개발 일정

| 단계 | 기간 | 목표 |
|------|------|------|
| 1단계 | 06.29 ~ 07.03 | 설계 및 데이터 구조 확정 |
| 2단계 | 07.04 ~ 07.09 | 핵심 기능 및 프로토타입 구현 |
| 3단계 | 07.10 ~ 07.13 | 통합 테스트 및 중간 보고서 작성 |
