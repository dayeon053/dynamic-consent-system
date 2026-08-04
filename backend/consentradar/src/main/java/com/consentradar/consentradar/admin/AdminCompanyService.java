package com.consentradar.consentradar.admin;

import com.consentradar.consentradar.admin.dto.CreateCompanyRequest;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.ConsentItemRepository;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import com.consentradar.consentradar.repository.RiskScoreRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 1-9 크롤링 대상 기업 관리(관리자). */
@Service
public class AdminCompanyService {

    private final CompanyRepository companyRepository;
    private final ConsentItemRepository consentItemRepository;
    private final PolicySnapshotRepository policySnapshotRepository;
    private final RiskScoreRepository riskScoreRepository;

    public AdminCompanyService(CompanyRepository companyRepository,
                                ConsentItemRepository consentItemRepository,
                                PolicySnapshotRepository policySnapshotRepository,
                                RiskScoreRepository riskScoreRepository) {
        this.companyRepository = companyRepository;
        this.consentItemRepository = consentItemRepository;
        this.policySnapshotRepository = policySnapshotRepository;
        this.riskScoreRepository = riskScoreRepository;
    }

    @Transactional
    public Company createCompany(CreateCompanyRequest request) {
        if (isBlank(request.companyName()) || isBlank(request.legalName()) || isBlank(request.category())
                || isBlank(request.packageName()) || isBlank(request.privacyUrl())) {
            throw new IllegalArgumentException("companyName, legalName, category, packageName, privacyUrl은 필수입니다.");
        }

        Company company = new Company();
        company.setCompanyName(request.companyName());
        company.setLegalName(request.legalName());
        company.setCategory(request.category());
        company.setPackageName(request.packageName());
        company.setPrivacyUrl(request.privacyUrl());
        company.setIsmsCertified(request.ismsCertified());

        try {
            return companyRepository.save(company);
        } catch (DataIntegrityViolationException e) {
            throw new CompanyConflictException("이미 등록된 packageName입니다: " + request.packageName());
        }
    }

    /**
     * 연관 데이터(ConsentItem/PolicySnapshot/RiskScore)가 하나라도 있으면 삭제를 거부한다(409).
     * Company에는 이 세 엔티티에 대해 cascade=ALL이 걸려있어 companyRepository.delete()만 호출해도
     * 조용히 다 같이 지워지는데, 그러면 지금까지 쌓인 위험도 산출 이력·동의 항목이 관리자의 회사
     * 등록 실수 정정 같은 가벼운 작업 한 번에 통째로 사라질 수 있다. 그래서 기본값은 "연관 데이터가
     * 있으면 거부"로 두고, 실제로 정리가 필요하면 그 연관 데이터부터 명시적으로 지우게 한다.
     */
    @Transactional
    public void deleteCompany(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 companyId: " + companyId));

        if (consentItemRepository.countByCompany_CompanyId(companyId) > 0
                || policySnapshotRepository.existsByCompany_CompanyId(companyId)
                || riskScoreRepository.existsByCompany_CompanyId(companyId)) {
            throw new CompanyConflictException(
                    "연관된 ConsentItem/PolicySnapshot/RiskScore가 있어 companyId=" + companyId + " 삭제를 거부합니다. "
                            + "연관 데이터를 먼저 정리해주세요.");
        }

        companyRepository.delete(company);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
