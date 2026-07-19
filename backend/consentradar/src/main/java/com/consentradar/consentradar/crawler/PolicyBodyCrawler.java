package com.consentradar.consentradar.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 기업 privacy_url 페이지를 Jsoup으로 크롤링해 광고/네비게이션 등 잡음을 제거한
 * 본문 텍스트만 추출한다. 요청 실패 시 최대 3회, 1s -> 2s -> 4s backoff로 재시도한다.
 * SPA 등으로 인해 HTTP 200이 와도 실제 콘텐츠가 렌더링되지 않은 경우(예: zero-width
 * 문자만 있는 빈 본문)를 걸러내기 위해 정제된 텍스트의 최소 길이도 검증한다.
 */
@Component
public class PolicyBodyCrawler {

    private static final Logger log = LoggerFactory.getLogger(PolicyBodyCrawler.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {1000L, 2000L, 4000L};
    private static final int TIMEOUT_MS = 10_000;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ConsentradarBot/1.0)";

    private static final String[] NOISE_SELECTORS = {
            "nav", "footer", "script", "style", "header", "iframe", "noscript",
            ".ad", ".ads", ".advertisement", ".banner", ".gnb", ".lnb"
    };

    private static final int MIN_MEANINGFUL_TEXT_LENGTH = 100;
    private static final Pattern ZERO_WIDTH_CHARS = Pattern.compile("[\\u200B\\u200C\\u200D\\uFEFF]");

    public String fetchCleanText(String url) {
        Document doc = fetchWithRetry(url);
        String text = cleanText(doc);

        int meaningfulLength = ZERO_WIDTH_CHARS.matcher(text).replaceAll("").trim().length();
        if (meaningfulLength < MIN_MEANINGFUL_TEXT_LENGTH) {
            throw new PolicyCrawlException(
                    url + " 크롤링 결과 텍스트가 너무 짧습니다 (" + meaningfulLength + "자, 최소 "
                            + MIN_MEANINGFUL_TEXT_LENGTH + "자 필요). SPA 등으로 실제 콘텐츠가 렌더링되지 않았을 수 있습니다.",
                    null);
        }

        return text;
    }

    Document fetchWithRetry(String url) {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return connectAndGet(url);
            } catch (IOException e) {
                lastError = e;
                log.warn("[Crawler] {} 요청 실패 ({}/{}회): {}", url, attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep(BACKOFF_MS[attempt - 1]);
                }
            }
        }
        throw new PolicyCrawlException(url + " 크롤링이 " + MAX_ATTEMPTS + "회 모두 실패했습니다.", lastError);
    }

    protected Document connectAndGet(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
    }

    static String cleanText(Document doc) {
        Document clone = doc.clone();
        for (String selector : NOISE_SELECTORS) {
            clone.select(selector).remove();
        }
        Element body = clone.body();
        return (body != null ? body : clone).text().trim();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PolicyCrawlException("크롤링 재시도 대기 중 인터럽트 발생", e);
        }
    }
}