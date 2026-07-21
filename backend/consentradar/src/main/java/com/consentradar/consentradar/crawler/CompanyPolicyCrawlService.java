package com.consentradar.consentradar.crawler;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.PolicySnapshot;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 등록된 전체 Company의 privacy_url을 순회하며 본문 텍스트를 크롤링해
 * PolicySnapshot으로 저장한다. (변경 여부 판단은 다루지 않음 — is_changed 미설정)
 */
@Service
public class CompanyPolicyCrawlService {

    private static final Logger log = LoggerFactory.getLogger(CompanyPolicyCrawlService.class);

    private final CompanyRepository companyRepository;
    private final PolicySnapshotRepository policySnapshotRepository;
    private final PolicyBodyCrawler policyBodyCrawler;

    public CompanyPolicyCrawlService(CompanyRepository companyRepository,
                                      PolicySnapshotRepository policySnapshotRepository,
                                      PolicyBodyCrawler policyBodyCrawler) {
        this.companyRepository = companyRepository;
        this.policySnapshotRepository = policySnapshotRepository;
        this.policyBodyCrawler = policyBodyCrawler;
    }

    public CrawlBatchResult crawlAll() {
        List<Company> companies = companyRepository.findAll();
        int successCount = 0;
        int failCount = 0;

        for (Company company : companies) {
            try {
                String rawText = policyBodyCrawler.fetchCleanText(company.getPrivacyUrl());

                PolicySnapshot snapshot = new PolicySnapshot();
                snapshot.setCompany(company);
                snapshot.setRawText(rawText);
                policySnapshotRepository.save(snapshot);

                successCount++;
                log.info("[Crawl] {} 수집 성공 ({}자)", company.getCompanyName(), rawText.length());
            } catch (Exception e) {
                failCount++;
                log.error("[Crawl] {} 수집 실패: {}", company.getCompanyName(), e.getMessage());
            }
        }

        log.info("[Crawl] 전체 {}건 중 성공 {}건, 실패 {}건", companies.size(), successCount, failCount);
        return new CrawlBatchResult(companies.size(), successCount, failCount);
    }
}