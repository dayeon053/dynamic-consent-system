package com.consentradar.consentradar.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyBodyCrawlerTest {

    private static final String SAMPLE_HTML = """
            <html>
              <head><style>.x{color:red}</style></head>
              <body>
                <header>사이트 헤더</header>
                <nav>메뉴 링크들</nav>
                <div class="banner">광고 배너</div>
                <div class="ad">광고 영역</div>
                <script>console.log('tracking')</script>
                <main>
                  <h1>개인정보처리방침</h1>
                  <p>본문 내용입니다. 수집하는 개인정보 항목은 이름, 이메일입니다.</p>
                </main>
                <footer>사이트 푸터 정보</footer>
              </body>
            </html>
            """;

    private static final String LONG_SAMPLE_HTML = """
            <html>
              <body>
                <main>
                  <h1>개인정보처리방침</h1>
                  <p>본 방침은 회사가 제공하는 서비스 이용 과정에서 이용자로부터 수집하는 개인정보의 항목,
                  수집 및 이용 목적, 보유 및 이용 기간, 제3자 제공 등에 관한 사항을 규정합니다.
                  이용자는 언제든지 개인정보 처리 현황을 확인하고 정정, 삭제를 요청할 수 있습니다.</p>
                </main>
              </body>
            </html>
            """;

    @Test
    void cleanText_removesNoiseTagsAndKeepsMainContent() {
        Document doc = Jsoup.parse(SAMPLE_HTML);

        String cleaned = PolicyBodyCrawler.cleanText(doc);

        assertTrue(cleaned.contains("개인정보처리방침"));
        assertTrue(cleaned.contains("수집하는 개인정보 항목은"));
        assertFalse(cleaned.contains("사이트 헤더"));
        assertFalse(cleaned.contains("메뉴 링크들"));
        assertFalse(cleaned.contains("광고 배너"));
        assertFalse(cleaned.contains("광고 영역"));
        assertFalse(cleaned.contains("사이트 푸터 정보"));
        assertFalse(cleaned.contains("tracking"));
    }

    @Test
    void fetchWithRetry_retriesUpToThreeTimesThenThrows() {
        AtomicInteger attempts = new AtomicInteger(0);
        PolicyBodyCrawler crawler = new PolicyBodyCrawler() {
            @Override
            protected Document connectAndGet(String url) throws IOException {
                attempts.incrementAndGet();
                throw new IOException("연결 실패");
            }
        };

        assertThrows(PolicyCrawlException.class, () -> crawler.fetchWithRetry("https://example.com"));
        assertEquals(3, attempts.get());
    }

    @Test
    void fetchWithRetry_succeedsAfterTransientFailures() {
        AtomicInteger attempts = new AtomicInteger(0);
        PolicyBodyCrawler crawler = new PolicyBodyCrawler() {
            @Override
            protected Document connectAndGet(String url) throws IOException {
                if (attempts.incrementAndGet() < 3) {
                    throw new IOException("일시적 오류");
                }
                return Jsoup.parse(SAMPLE_HTML);
            }
        };

        Document result = crawler.fetchWithRetry("https://example.com");

        assertEquals(3, attempts.get());
        assertTrue(result.text().contains("개인정보처리방침"));
    }

    @Test
    void fetchCleanText_throwsWhenBodyIsEmpty() {
        PolicyBodyCrawler crawler = new PolicyBodyCrawler() {
            @Override
            protected Document connectAndGet(String url) {
                return Jsoup.parse("<html><body></body></html>");
            }
        };

        PolicyCrawlException ex = assertThrows(PolicyCrawlException.class,
                () -> crawler.fetchCleanText("https://spa.example.com"));
        assertTrue(ex.getMessage().contains("너무 짧습니다"));
    }

    @Test
    void fetchCleanText_throwsWhenBodyIsOnlyZeroWidthCharacters() {
        // 토스(toss.im/privacy-policy)처럼 Next.js SPA가 실제 콘텐츠 없이
        // zero-width 문자만 정적 HTML에 남기는 경우를 재현한다.
        String zeroWidthOnly = "‌ ".repeat(30); // 60자 분량이지만 의미 있는 문자는 0자
        PolicyBodyCrawler crawler = new PolicyBodyCrawler() {
            @Override
            protected Document connectAndGet(String url) {
                return Jsoup.parse("<html><body>" + zeroWidthOnly + "</body></html>");
            }
        };

        assertThrows(PolicyCrawlException.class, () -> crawler.fetchCleanText("https://spa.example.com"));
    }

    @Test
    void fetchCleanText_throwsWhenBodyIsShorterThanMinimumLength() {
        PolicyBodyCrawler crawler = new PolicyBodyCrawler() {
            @Override
            protected Document connectAndGet(String url) {
                return Jsoup.parse("<html><body><p>너무 짧은 본문입니다.</p></body></html>");
            }
        };

        assertThrows(PolicyCrawlException.class, () -> crawler.fetchCleanText("https://short.example.com"));
    }

    @Test
    void fetchCleanText_succeedsWhenBodyMeetsMinimumLength() {
        PolicyBodyCrawler crawler = new PolicyBodyCrawler() {
            @Override
            protected Document connectAndGet(String url) {
                return Jsoup.parse(LONG_SAMPLE_HTML);
            }
        };

        String text = crawler.fetchCleanText("https://example.com");

        assertTrue(text.contains("개인정보처리방침"));
    }
}
