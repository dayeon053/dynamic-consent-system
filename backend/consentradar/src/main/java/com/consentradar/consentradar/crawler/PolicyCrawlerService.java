package com.consentradar.consentradar.crawler;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.RiskScore;
import com.consentradar.consentradar.pipeline.RiskPipelineService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PolicyCrawlerService {

    private final RiskPipelineService riskPipelineService;

    public PolicyCrawlerService(RiskPipelineService riskPipelineService) {
        this.riskPipelineService = riskPipelineService;
    }

    public List<RiskScore> crawlAndAnalyze(CrawlTarget target, Company company) {
        return riskPipelineService.run(target, company);
    }
}