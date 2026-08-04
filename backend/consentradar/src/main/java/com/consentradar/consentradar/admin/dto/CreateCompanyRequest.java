package com.consentradar.consentradar.admin.dto;

/** POST /admin/companies 요청 바디 */
public record CreateCompanyRequest(
        String companyName,
        String legalName,
        String category,
        String packageName,
        String privacyUrl,
        boolean ismsCertified
) {
}
