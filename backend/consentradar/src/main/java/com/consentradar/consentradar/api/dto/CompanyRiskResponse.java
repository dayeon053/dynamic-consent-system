package com.consentradar.consentradar.api.dto;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.RiskScore;

import java.math.BigDecimal;

/** GET /companies 응답 항목 1개 */
public class CompanyRiskResponse {

    private final Long companyId;
    private final String companyName;
    private final String packageName;
    private final String privacyUrl;
    private final boolean ismsCertified;
    private final BigDecimal riskScore;
    private final String riskGrade;

    public CompanyRiskResponse(Company company, RiskScore representativeScore) {
        this.companyId     = company.getCompanyId();
        this.companyName   = company.getCompanyName();
        this.packageName   = company.getPackageName();
        this.privacyUrl    = company.getPrivacyUrl();
        this.ismsCertified = company.isIsmsCertified();
        this.riskScore     = representativeScore != null ? representativeScore.getTotalScore() : null;
        this.riskGrade     = representativeScore != null ? representativeScore.getGrade().name() : null;
    }

    public Long      getCompanyId()     { return companyId; }
    public String    getCompanyName()   { return companyName; }
    public String    getPackageName()   { return packageName; }
    public String    getPrivacyUrl()    { return privacyUrl; }
    public boolean   isIsmsCertified()  { return ismsCertified; }
    public BigDecimal getRiskScore()    { return riskScore; }
    public String    getRiskGrade()     { return riskGrade; }
}
