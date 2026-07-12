package com.consentradar.consentradar.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.OffsetDateTime;

@Component
public class PolicyCrawler {

    private static final int TIMEOUT_MS = 10_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; ConsentradarBot/1.0)";
    private static final int MIN_VALID_LENGTH = 200;

    public CrawledPolicyDto crawl(CrawlTarget target) {
        Document doc;
        try {
            doc = Jsoup.connect(target.getPolicyUrl())
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
        } catch (IOException e) {
            throw new RuntimeException("[" + target.getCompanyName() + "] 페이지 요청 실패", e);
        }

        Element content = doc.selectFirst(target.getContentSelector());
        if (content == null) {
            throw new RuntimeException("[" + target.getCompanyName() + "] 본문 셀렉터로 요소를 찾을 수 없음");
        }

        String text = content.text();
        if (text.trim().length() < MIN_VALID_LENGTH) {
            throw new RuntimeException("[" + target.getCompanyName() + "] 텍스트가 너무 짧음. JS 렌더링 필요할 수 있음");
        }

        return new CrawledPolicyDto(
                target.getCompanyName(),
                target.getPolicyUrl(),
                text,
                OffsetDateTime.now()
        );
    }
}