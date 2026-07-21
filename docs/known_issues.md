# Known Issues

## 배달의민족·토스 개인정보처리방침 페이지 — Jsoup 크롤링 불가 (SPA 구조)

작업일: 2026-07-19
관련 코드: `CompanyPolicyCrawlService`, `PolicyBodyCrawler`

### 증상
로컬 MySQL의 실제 기업 5건(카카오/네이버/배달의민족/토스/당근마켓)을 대상으로
`CompanyPolicyCrawlService.crawlAll()`을 실제 네트워크로 검증한 결과, 카카오/네이버/당근마켓은
정상적으로 본문 텍스트가 수집됐지만 배달의민족과 토스는 실패했다.

- **배달의민족** (`www.baemin.com`, `terms.baemin.com`): HTTP 404. 사이트 자체가
  React/Next.js 기반 SPA라서 `<div id="root"></div>` + JS 번들만 있고 정적 HTML에
  실제 텍스트가 없음. 크롤링 가능한 정적 URL을 찾지 못함 (동일 법인인 우아한형제들이
  운영하는 "배민문방구" 스토어 정책 페이지(`store.baemin.com/service/private.php`)는
  정적 HTML로 존재하지만, 배달앱 자체의 정책 문서와 동일한지 확인되지 않아 채택하지 않음).
- **토스** (`toss.im/privacy-policy`): HTTP 200으로 응답하지만 Jsoup으로 추출한
  본문(`body.text()`)이 zero-width 문자 35자뿐인 사실상 빈 텍스트. 이 페이지도 Next.js
  SPA이며, 실제 정책 텍스트는 `<script id="__NEXT_DATA__">` JSON 안에만 존재하고
  화면에 정적으로 렌더링되지 않음. `curl | grep`으로는 "개인정보처리방침" 문자열이
  잡혀서 처음엔 정상 URL로 오판했으나, 그건 저 JSON 블록 안의 문자열이었을 뿐
  실제 렌더링된 본문이 아니었다.

### 영향
`CompanyPolicyCrawlService.crawlAll()`은 예외가 안 나면 무조건 성공으로 집계하므로,
토스 같은 케이스(HTTP 200 + 빈 본문)는 **배치 결과상 성공으로 잡히지만 실제로는
의미 있는 데이터가 저장되지 않는 silent failure**다. 현재는 raw_text 내용 검증
로직이 없다.

### 임시 조치
- 배달의민족: `privacy_url`을 기존 값(`https://www.baemin.com/policy/privacy`, 404) 그대로 유지.
  NULL로 바꾸지 않은 이유는 `Company.privacyUrl`이 `nullable = false`라서 NULL 저장 시
  제약조건 위반 위험이 있기 때문.
- 토스: `privacy_url`을 `https://toss.im/privacy-policy`로 갱신은 했으나, 이 URL도
  크롤링이 안 되는 건 동일 (2026-07-19 기준 검증 제외 대상).

### 근본 해결을 위해 검토할 것
- Headless 브라우저(Playwright/Selenium 등) 도입해 JS 렌더링 후 텍스트 추출
- 또는 `raw_text` 길이/내용에 대한 최소 검증(예: N자 미만이면 실패로 재분류)을
  `CompanyPolicyCrawlService`에 추가해 최소한 silent failure는 걸러내기