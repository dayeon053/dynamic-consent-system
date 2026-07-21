package com.consentradar.consentradar.api.dto;

import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.entity.RiskScore;
import com.dynamicconsent.model.RiskResult;

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

    /**
     * 개인 맞춤 위험도(필수동의 + 사용자가 실제 체크한 선택동의 기준)로 응답을 만들 때 사용.
     * 배치 파이프라인이 저장한 RiskScore(전체 항목 기준, 유저 비특정)를 그대로 노출하지 않기 위해
     * RiskResult를 직접 받는 생성자를 별도로 둔다.
     */
    public CompanyRiskResponse(Company company, RiskResult personalResult) {
        this.companyId     = company.getCompanyId();
        this.companyName   = company.getCompanyName();
        this.packageName   = company.getPackageName();
        this.privacyUrl    = company.getPrivacyUrl();
        this.ismsCertified = company.isIsmsCertified();
        this.riskScore     = personalResult != null ? BigDecimal.valueOf(personalResult.getScore()) : null;
        this.riskGrade     = personalResult != null ? personalResult.getGrade().name() : null;
    }

    public Long      getCompanyId()     { return companyId; }
    public String    getCompanyName()   { return companyName; }
    public String    getPackageName()   { return packageName; }
    public String    getPrivacyUrl()    { return privacyUrl; }
    public boolean   isIsmsCertified()  { return ismsCertified; }
    public BigDecimal getRiskScore()    { return riskScore; }
    public String    getRiskGrade()     { return riskGrade; }
}
