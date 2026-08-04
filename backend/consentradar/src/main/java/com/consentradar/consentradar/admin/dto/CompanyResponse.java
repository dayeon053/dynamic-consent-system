package com.consentradar.consentradar.admin.dto;

import com.consentradar.consentradar.entity.Company;

import java.time.LocalDateTime;

/** POST /admin/companies 응답 */
public class CompanyResponse {

    private final Long companyId;
    private final String companyName;
    private final String legalName;
    private final String category;
    private final String packageName;
    private final String privacyUrl;
    private final boolean ismsCertified;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public CompanyResponse(Company company) {
        this.companyId     = company.getCompanyId();
        this.companyName   = company.getCompanyName();
        this.legalName     = company.getLegalName();
        this.category      = company.getCategory();
        this.packageName   = company.getPackageName();
        this.privacyUrl    = company.getPrivacyUrl();
        this.ismsCertified = company.isIsmsCertified();
        this.createdAt     = company.getCreatedAt();
        this.updatedAt     = company.getUpdatedAt();
    }

    public Long getCompanyId()          { return companyId; }
    public String getCompanyName()      { return companyName; }
    public String getLegalName()        { return legalName; }
    public String getCategory()         { return category; }
    public String getPackageName()      { return packageName; }
    public String getPrivacyUrl()       { return privacyUrl; }
    public boolean isIsmsCertified()    { return ismsCertified; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
