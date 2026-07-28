package com.consentradar.consentradar.crawler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR #32 리뷰 코멘트 재현 테스트: {@link PolicyBodyCrawler}는 싱글턴 빈으로 Playwright
 * {@code Browser}를 한 번만 만들어 재사용하는데, 실제로는 스케줄러 스레드(매일 새벽 3시)와
 * 관리자 수동 트리거(POST /admin/crawl/{companyId}, HTTP 요청 스레드)가 같은 빈을 서로 다른
 * 스레드에서 동시에 호출할 수 있다. Playwright는 생성한 스레드에서만 사용해야 한다는 제약이
 * 있어 이 시나리오를 실제 Chromium으로 재현한다.
 *
 * DB/Spring 컨텍스트가 필요 없으므로 @Tag("integration")을 붙이지 않았다 — 로컬 HTTP
 * 서버 + 실제 Playwright만 있으면 된다.
 */
class PolicyBodyCrawlerConcurrencyTest {

    private static final String SPA_SHELL_HTML = """
            <html>
              <body>
                <div id="app"></div>
                <script>
                  document.getElementById('app').innerHTML =
                    '<main><h1>개인정보처리방침</h1>' +
                    '<p>본 방침은 회사가 제공하는 서비스 이용 과정에서 이용자로부터 수집하는 ' +
                    '개인정보의 항목, 수집 및 이용 목적, 보유 및 이용 기간, 제3자 제공 등에 ' +
                    '관한 사항을 규정합니다. 이용자는 언제든지 개인정보 처리 현황을 확인하고 ' +
                    '정정, 삭제를 요청할 수 있습니다.</p></main>';
                </script>
              </body>
            </html>
            """;

    private static HttpServer server;
    private static String policyUrl;

    @BeforeAll
    static void startLocalSpaServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/policy", exchange -> {
            byte[] body = SPA_SHELL_HTML.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        policyUrl = "http://localhost:" + server.getAddress().getPort() + "/policy";
    }

    @AfterAll
    static void stopLocalSpaServer() {
        server.stop(0);
    }

    /**
     * 스케줄러 스레드 1개 + 관리자 수동 트리거 HTTP 스레드 여러 개가 같은
     * {@code PolicyBodyCrawler} 빈을 거의 동시에 호출하는 상황을 흉내낸다.
     * CyclicBarrier로 모든 스레드가 (1) Browser lazy 초기화 시점과 (2) 초기화 이후
     * newPage() 호출 시점 양쪽 모두에서 최대한 겹치도록 만든다.
     */
    @RepeatedTest(5)
    void schedulerAndAdminTriggerCallingSameCrawlerBeanConcurrently() throws Exception {
        PolicyBodyCrawler sharedCrawler = new PolicyBodyCrawler(); // 실제 싱글턴 빈과 동일하게 인스턴스 하나 공유

        int threadCount = 6; // 스케줄러 스레드 1개 + 동시다발 관리자 수동 트리거 여러 건 가정
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                barrier.await();
                return sharedCrawler.fetchCleanText(policyUrl);
            });
        }

        List<Future<String>> futures = pool.invokeAll(tasks, 60, TimeUnit.SECONDS);
        pool.shutdown();

        List<String> results = new ArrayList<>();
        List<Exception> failures = new ArrayList<>();
        for (Future<String> future : futures) {
            try {
                if (future.isCancelled()) {
                    failures.add(new java.util.concurrent.TimeoutException("작업이 60초 내에 끝나지 않음(행 걸림 의심)"));
                } else {
                    results.add(future.get());
                }
            } catch (ExecutionException e) {
                failures.add(e);
            }
        }

        sharedCrawler.close();

        assertTrue(failures.isEmpty(),
                () -> "스케줄러/관리자 트리거 동시 호출 중 예외 또는 행(hang) 발생: " + failures);
        assertEquals(threadCount, results.size());
        for (String text : results) {
            assertTrue(text.contains("개인정보처리방침"), () -> "렌더링 결과가 예상과 다름: " + text);
        }
    }
}
