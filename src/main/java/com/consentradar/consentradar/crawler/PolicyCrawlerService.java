package com.consentradar.consentradar.crawler;

import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class PolicyCrawlerService {

    private final PolicyCrawler policyCrawler;
    private final LlmClient llmClient;
    private final PolicySnapshotRepository policySnapshotRepository;

    public PolicyCrawlerService(PolicyCrawler policyCrawler,
                                LlmClient llmClient,
                                PolicySnapshotRepository policySnapshotRepository) {
        this.policyCrawler = policyCrawler;
        this.llmClient = llmClient;
        this.policySnapshotRepository = policySnapshotRepository;
    }

    @Transactional
    public void crawlAndSave(CrawlTarget target, Company company) {
        // 1. 크롤링
        CrawledPolicyDto crawled = policyCrawler.crawl(target);

        // 2. LLM 호출
        String llmResponse = llmClient.call(crawled.getRawText());

        // 3. DB 저장
        PolicySnapshot snapshot = new PolicySnapshot();
        snapshot.setCompany(company);
        snapshot.setRawText(crawled.getRawText());
        snapshot.setChanged(false);
        snapshot.setCrawledAt(LocalDateTime.now());

        policySnapshotRepository.save(snapshot);
    }
}