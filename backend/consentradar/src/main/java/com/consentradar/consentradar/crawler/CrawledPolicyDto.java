package com.consentradar.consentradar.crawler;

import java.time.OffsetDateTime;

public class CrawledPolicyDto {

    private String companyName;
    private String policyUrl;
    private String rawText;
    private OffsetDateTime collectedAt;

    public CrawledPolicyDto(String companyName, String policyUrl,
                            String rawText, OffsetDateTime collectedAt) {
        this.companyName = companyName;
        this.policyUrl = policyUrl;
        this.rawText = rawText;
        this.collectedAt = collectedAt;
    }

    public String getCompanyName() { return companyName; }
    public String getPolicyUrl() { return policyUrl; }
    public String getRawText() { return rawText; }
    public OffsetDateTime getCollectedAt() { return collectedAt; }
}