package com.consentradar.consentradar.crawler;

public class CrawlTarget {

    private final String companyName;
    private final String policyUrl;
    private final String contentSelector;

    public CrawlTarget(String companyName, String policyUrl, String contentSelector) {
        this.companyName = companyName;
        this.policyUrl = policyUrl;
        this.contentSelector = contentSelector;
    }

    // 카카오 약관 페이지 (셀렉터는 실제 페이지 확인 후 수정 필요)
    public static CrawlTarget kakao() {
        return new CrawlTarget(
                "카카오",
                "https://www.kakaocorp.com/page/detail/9610",
                "body"
        );
    }

    public String getCompanyName() { return companyName; }
    public String getPolicyUrl() { return policyUrl; }
    public String getContentSelector() { return contentSelector; }
}