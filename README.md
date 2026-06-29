# Dynamic Consent System

개인정보 처리 약관을 자동 분석하여 위험도를 정량화하고, 사용자의 동의 철회를 돕는 Android 앱.

---

## 팀 구성

| 역할 | 이름 | GitHub ID |
|------|------|-----------|
| 백엔드A | 노가현 | (GitHub ID 입력) |
| 백엔드B | 황다연 | (GitHub ID 입력) |
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

## 🔒 main과 develop 브랜치에 직접 push하면 안 되는 이유

### main 브랜치란?

`main`은 **언제든 제출/배포할 수 있는 완성된 코드만 있는 공간**입니다.

여기에 직접 push하면 안 되는 이유는 단 하나입니다.

> 4명이 동시에 main에 push하면, 나중에 올린 사람 코드가 먼저 올린 사람 코드를 **덮어씌워서 사라지게** 만들 수 있습니다.

main은 아무도 직접 손대지 않고, 오직 **develop에서 최종 검증이 끝난 코드**만 PR을 통해 들어옵니다.

### develop 브랜치란?

`develop`은 각자 만든 기능들을 **하나로 합쳐서 같이 잘 돌아가는지 확인하는 공간**입니다.

여기에도 직접 push하면 안 되는 이유:

> 내 코드가 다른 사람 코드와 충돌하는지, 합쳐도 문제없는지 확인하지 않고 올리면 **팀 전체의 develop이 망가집니다.** 한 명이 실수로 직접 push하면 나머지 3명이 최신 코드를 받아갔을 때 빌드가 안 되거나 에러가 납니다.

그래서 develop에 코드를 넣을 때도 반드시 PR → 승인 → Merge 순서를 지킵니다.

### main과 develop은 언제 건드리나?

| 브랜치 | 건드리는 시점 | 누가 |
|--------|-------------|------|
| `develop` | 기능 브랜치(`feature/...`)에서 PR을 올려서 Merge될 때 | PR 올린 본인이 Merge 버튼 클릭 |
| `main` | 개발 단계가 완전히 끝나고 최종 제출 직전에 develop → main PR 한 번 | 팀 전체 합의 후 |

즉, **개발 기간 중에 main을 직접 건드릴 일은 거의 없습니다.** 최종 제출 직전에 한 번만 develop → main으로 합칩니다.

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

## 📋 PR (Pull Request) 규칙

### PR이란?

> "내가 만든 기능을 `develop`에 합쳐도 되는지 팀원한테 확인받는 과정"

직접 develop에 push하는 게 아니라, "나 이거 올려도 돼?" 라고 요청을 보내면 팀원이 확인하고 허락해줘야 합쳐지는 방식입니다. 이 과정 없이 바로 합치는 건 브랜치 보호 설정으로 막혀 있어서 물리적으로 불가능합니다.

### PR 여는 방법

1. GitHub 웹사이트 들어가기
2. 상단에 뜨는 노란 배너 **"Compare & pull request"** 클릭
   (안 뜨면 본인 브랜치 선택 후 **"Contribute"** → **"Open pull request"**)
3. **Base:** `develop`, **Compare:** 내 브랜치 확인
4. 양식 채우기
5. **Reviewers**에 파트 파트너 지정 후 제출

### PR 제목 규칙

```
[BE] 크롤러 기본 구조 구현
[FE] 위험기관 리스트 화면 레이아웃
[RISK] 5대 변수 Enum 클래스 작성
[FIX] 로그인 화면 null 오류 수정
```

### 리뷰어 배정 기준

| PR 올린 사람 | 리뷰어 |
|-------------|--------|
| 팀원1 (노가현, BE) | 팀원3 (황다연) |
| 팀원3 (황다연, RISK) | 팀원1 (노가현) |
| 팀원2 (김도영, FE) | 팀원4 (이재은) |
| 팀원4 (이재은, FE) | 팀원2 (김도영) |

---

## ✅ PR 승인 기준

우리 팀은 클로드 코드로 개발하기 때문에 코드 한 줄씩 직접 검토하는 방식은 사용하지 않습니다. 대신 아래 기준으로 승인 여부를 판단합니다.

### 승인해도 되는 조건 — 아래 3가지 모두 해당할 때

**1. PR 설명이 제대로 채워져 있다**
- 제목에 `[BE]`, `[FE]`, `[RISK]`, `[FIX]` 태그가 있다
- "어떤 작업을 했나요?" 란이 비어있지 않다
- 체크리스트 항목이 모두 체크되어 있다

**2. 내 파트 폴더를 건드리지 않았다**
- GitHub PR 화면의 **"Files changed"** 탭에서 변경된 파일 목록만 확인
- 프론트 PR인데 `backend/` 파일이 포함됨 → 이유 물어보기
- 백엔드 PR인데 `frontend/` 파일이 포함됨 → 이유 물어보기
- `common-model/`은 팀원3 관리 영역이므로 다른 사람이 수정했다면 확인

**3. 변경된 파일 수가 너무 많지 않다**
- 한 PR에 수십 개 파일이 한꺼번에 올라왔다면 기능 단위가 너무 크다는 신호
- 댓글로 "기능별로 나눠서 올려줘" 요청

### 승인 거부(Request changes) 하는 경우

| 상황 | 댓글 예시 |
|------|----------|
| PR 설명이 거의 비어있다 | "어떤 작업인지 설명 추가해줘" |
| 체크리스트 미체크 항목 있다 | "체크리스트 확인 후 다시 요청해줘" |
| 내 파트 파일이 같이 수정됐다 | "이 파일 왜 수정됐어? 확인해줘" |
| 파일이 너무 많이 한꺼번에 바뀌었다 | "너무 큰 단위야, 기능별로 나눠서 올려줘" |

### 실제 승인 절차 (30초면 됨)

```
1. GitHub → Pull requests 탭 클릭
2. 해당 PR 클릭
3. "Files changed" 탭 → 바뀐 파일 목록만 훑기 (어느 폴더인지만 확인)
4. "Conversation" 탭 → PR 설명 + 체크리스트 확인
5. 이상 없으면 우측 상단 "Review changes" → "Approve" → "Submit review"
   문제 있으면 "Request changes" 선택 후 댓글 작성
```

### Merge 조건 및 방법

- 리뷰어 **1명 이상** Approve ✅ 받아야 Merge 가능 (GitHub이 자동으로 막음)
- Merge는 PR 올린 **본인이 직접** 함 (리뷰어가 하지 않음)
- Merge 방식: **"Squash and merge"** 선택 (커밋 목록이 깔끔하게 정리됨)
- Merge 후 **"Delete branch"** 버튼 클릭해서 브랜치 삭제

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

충돌이란 두 사람이 같은 파일의 같은 줄을 동시에 수정했을 때 발생합니다. 흔한 일이니 당황하지 않아도 됩니다.

```bash
# 충돌 발생 시
git pull origin develop     # 최신 develop 코드 가져오기
# 충돌난 파일을 열면 아래처럼 표시됨:
# <<<<<<< HEAD
# 내가 쓴 코드
# =======
# 남이 쓴 코드
# >>>>>>> develop

# 두 코드 중 남길 것만 정리한 뒤 저장하고:
git add .
git commit -m "fix: develop 브랜치와 충돌 해결"
git push origin feature/내브랜치
```

모르겠으면 혼자 해결하려 하지 말고 팀원 부르기.

---

## 🛠️ 처음 세팅 방법 (클론부터 시작)

```bash
# 1. 내 컴퓨터에 저장소 가져오기
git clone https://github.com/dayeon053/dynamic-consent-system.git

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
