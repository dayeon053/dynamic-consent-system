package com.consentradar.consentradar.crawler;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 기업 privacy_url 페이지에서 본문 텍스트만 추출하는 하이브리드 크롤러.
 *
 * 1) 먼저 Jsoup(정적 HTML 요청)으로 시도한다 — 빠르고 가벼우며 대다수 정적 사이트는
 *    이 단계에서 끝난다. 요청 실패 시 최대 3회, 1s -> 2s -> 4s backoff로 재시도한다.
 * 2) Jsoup 결과 텍스트가 최소 길이({@link #MIN_MEANINGFUL_TEXT_LENGTH})에 못 미치면
 *    (SPA라 정적 HTML에 실제 콘텐츠가 없는 경우) 헤드리스 브라우저(Playwright Chromium)로
 *    페이지를 실제 렌더링해 재시도한다. 렌더링된 HTML은 동일한 노이즈 제거/본문 추출
 *    로직({@link #cleanText(Document)})을 그대로 통과시킨다.
 * 3) 헤드리스 브라우저로도 최소 길이를 못 채우면 {@link PolicyCrawlException}으로 실패 처리한다.
 *
 * 정적 사이트만 쓰는 기업은 1단계에서 끝나므로 기존 동작·속도에 영향이 없고, SPA 기업이
 * 추가되어도 코드 변경 없이 자동으로 2단계가 적용된다.
 */
@Component
public class PolicyBodyCrawler {

    private static final Logger log = LoggerFactory.getLogger(PolicyBodyCrawler.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {1000L, 2000L, 4000L};
    private static final int TIMEOUT_MS = 10_000;
    private static final int HEADLESS_TIMEOUT_MS = 20_000;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ConsentradarBot/1.0)";

    private static final String[] NOISE_SELECTORS = {
            "nav", "footer", "script", "style", "header", "iframe", "noscript",
            ".ad", ".ads", ".advertisement", ".banner", ".gnb", ".lnb"
    };

    private static final int MIN_MEANINGFUL_TEXT_LENGTH = 100;
    private static final Pattern ZERO_WIDTH_CHARS = Pattern.compile("[\\u200B\\u200C\\u200D\\uFEFF]");

    // 헤드리스 브라우저는 기동 비용이 크므로(수백ms~수초) 최초 SPA 폴백이 필요할 때 한 번만
    // 띄우고 재사용한다. 정적 사이트만 처리하는 동안에는 전혀 생성되지 않는다.
    //
    // Playwright는 Browser/Page 등 자신이 만든 객체를 생성한 스레드에서만 써야 한다는
    // 제약이 있다(공식 문서 "Threading" 섹션). 이 빈은 싱글턴이라 스케줄러 스레드(매일 새벽
    // 3시 자동 실행)와 관리자 수동 트리거(POST /admin/crawl/{companyId}, HTTP 요청 스레드)
    // 양쪽에서 동시에 호출될 수 있으므로, Playwright/Browser 생성과 모든 페이지 조작을
    // 전용 단일 스레드(playwrightExecutor)로만 보내 실행한다 — 실제로 두 스레드가 동시에
    // browser().newPage()를 호출하면 "Cannot find object to call __adopt__" 등 Playwright
    // 프로토콜 오류가 재현된다(PolicyBodyCrawlerConcurrencyTest).
    private final ExecutorService playwrightExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "playwright-crawler");
        t.setDaemon(true);
        return t;
    });
    private Playwright playwright;
    private Browser browser;

    public String fetchCleanText(String url) {
        Document doc = fetchWithRetry(url);
        String text = cleanText(doc);
        int meaningfulLength = meaningfulLength(text);

        if (meaningfulLength >= MIN_MEANINGFUL_TEXT_LENGTH) {
            return text;
        }

        log.warn("[Crawler] {} Jsoup 결과 텍스트 부족({}자, 최소 {}자 필요) — SPA로 판단하고 헤드리스 브라우저로 재시도",
                url, meaningfulLength, MIN_MEANINGFUL_TEXT_LENGTH);

        String renderedHtml = fetchRenderedHtml(url);
        Document renderedDoc = Jsoup.parse(renderedHtml, url);
        String renderedText = cleanText(renderedDoc);
        int renderedLength = meaningfulLength(renderedText);

        if (renderedLength < MIN_MEANINGFUL_TEXT_LENGTH) {
            throw new PolicyCrawlException(
                    url + " 크롤링 결과 텍스트가 너무 짧습니다 (Jsoup " + meaningfulLength
                            + "자, 헤드리스 브라우저 " + renderedLength + "자, 최소 " + MIN_MEANINGFUL_TEXT_LENGTH
                            + "자 필요). SPA 등으로 실제 콘텐츠가 렌더링되지 않았을 수 있습니다.",
                    null);
        }

        log.info("[Crawler] {} 헤드리스 브라우저로 재수집 성공 ({}자)", url, renderedLength);
        return renderedText;
    }

    private int meaningfulLength(String text) {
        return ZERO_WIDTH_CHARS.matcher(text).replaceAll("").trim().length();
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

    /**
     * 헤드리스 Chromium으로 페이지를 실제 로드하고, JS 렌더링이 끝날 때까지
     * (네트워크가 유휴 상태가 될 때까지) 기다린 뒤 최종 HTML을 반환한다.
     *
     * 호출한 스레드가 무엇이든(스케줄러 스레드든 관리자 HTTP 요청 스레드든) 실제 Playwright
     * 호출은 항상 {@link #playwrightExecutor}의 전용 스레드 위에서만 실행되도록 위임한다.
     */
    protected String fetchRenderedHtml(String url) {
        try {
            return playwrightExecutor.submit(() -> renderOnPlaywrightThread(url)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PolicyCrawlException(url + " 헤드리스 브라우저 렌더링 중 인터럽트 발생", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PolicyCrawlException policyCrawlException) {
                throw policyCrawlException;
            }
            throw new PolicyCrawlException(url + " 헤드리스 브라우저 렌더링 실패: " + cause.getMessage(), cause);
        }
    }

    private String renderOnPlaywrightThread(String url) {
        try {
            Page page = browser().newPage(new Browser.NewPageOptions().setUserAgent(USER_AGENT));
            try {
                page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(HEADLESS_TIMEOUT_MS)
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));
                return page.content();
            } finally {
                page.close();
            }
        } catch (PlaywrightException e) {
            throw new PolicyCrawlException(url + " 헤드리스 브라우저 렌더링 실패: " + e.getMessage(), e);
        }
    }

    /**
     * playwrightExecutor의 전용 스레드에서만 호출되므로(단일 스레드 executor) 동시 접근이
     * 없어 별도 동기화가 필요 없다.
     */
    private Browser browser() {
        if (browser == null) {
            log.info("[Crawler] 헤드리스 브라우저(Playwright Chromium) 최초 기동");
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        }
        return browser;
    }

    @PreDestroy
    public void close() {
        try {
            playwrightExecutor.submit(() -> {
                if (browser != null) {
                    browser.close();
                    browser = null;
                }
                if (playwright != null) {
                    playwright.close();
                    playwright = null;
                }
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.warn("[Crawler] Playwright 종료 중 오류: {}", e.getMessage());
        } finally {
            playwrightExecutor.shutdown();
        }
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
