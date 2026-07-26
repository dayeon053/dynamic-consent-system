package com.consentradar.consentradar.admin;

import com.consentradar.consentradar.admin.dto.CreateCompanyRequest;
import com.consentradar.consentradar.entity.Company;
import com.consentradar.consentradar.repository.CompanyRepository;
import com.consentradar.consentradar.repository.ConsentItemRepository;
import com.consentradar.consentradar.repository.PolicySnapshotRepository;
import com.consentradar.consentradar.repository.RiskScoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCompanyServiceTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private ConsentItemRepository consentItemRepository;
    @Mock private PolicySnapshotRepository policySnapshotRepository;
    @Mock private RiskScoreRepository riskScoreRepository;

    private AdminCompanyService newService() {
        return new AdminCompanyService(companyRepository, consentItemRepository, policySnapshotRepository, riskScoreRepository);
    }

    @Test
    void createCompany_savesAndReturnsCompany_whenRequestIsValid() {
        CreateCompanyRequest request = new CreateCompanyRequest("카카오", "com.kakao.talk", "https://privacy.example.com", true);
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Company saved = newService().createCompany(request);

        assertEquals("카카오", saved.getCompanyName());
        assertEquals("com.kakao.talk", saved.getPackageName());
        assertEquals("https://privacy.example.com", saved.getPrivacyUrl());
        assertEquals(true, saved.isIsmsCertified());
    }

    @Test
    void createCompany_throwsIllegalArgumentException_whenCompanyNameIsBlank() {
        CreateCompanyRequest request = new CreateCompanyRequest("  ", "com.kakao.talk", "https://privacy.example.com", false);

        assertThrows(IllegalArgumentException.class, () -> newService().createCompany(request));
        verify(companyRepository, never()).save(any());
    }

    @Test
    void createCompany_throwsCompanyConflictException_whenPackageNameAlreadyExists() {
        CreateCompanyRequest request = new CreateCompanyRequest("카카오", "com.kakao.talk", "https://privacy.example.com", false);
        when(companyRepository.save(any(Company.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(CompanyConflictException.class, () -> newService().createCompany(request));
    }

    @Test
    void deleteCompany_deletes_whenNoRelatedDataExists() {
        Company company = new Company();
        company.setCompanyId(1L);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(consentItemRepository.countByCompany_CompanyId(1L)).thenReturn(0L);
        when(policySnapshotRepository.existsByCompany_CompanyId(1L)).thenReturn(false);
        when(riskScoreRepository.existsByCompany_CompanyId(1L)).thenReturn(false);

        newService().deleteCompany(1L);

        verify(companyRepository, times(1)).delete(company);
    }

    @Test
    void deleteCompany_throwsIllegalArgumentException_whenCompanyNotFound() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> newService().deleteCompany(99L));
        verify(companyRepository, never()).delete(any());
    }

    @Test
    void deleteCompany_throwsCompanyConflictException_whenConsentItemsExist() {
        Company company = new Company();
        company.setCompanyId(1L);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(consentItemRepository.countByCompany_CompanyId(1L)).thenReturn(2L);

        assertThrows(CompanyConflictException.class, () -> newService().deleteCompany(1L));
        verify(companyRepository, never()).delete(any());
    }

    @Test
    void deleteCompany_throwsCompanyConflictException_whenRiskScoresExist() {
        Company company = new Company();
        company.setCompanyId(1L);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(consentItemRepository.countByCompany_CompanyId(1L)).thenReturn(0L);
        when(policySnapshotRepository.existsByCompany_CompanyId(1L)).thenReturn(false);
        when(riskScoreRepository.existsByCompany_CompanyId(1L)).thenReturn(true);

        assertThrows(CompanyConflictException.class, () -> newService().deleteCompany(1L));
        verify(companyRepository, never()).delete(any());
    }
}
