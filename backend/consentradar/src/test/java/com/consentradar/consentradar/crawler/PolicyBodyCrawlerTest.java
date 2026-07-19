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
}
