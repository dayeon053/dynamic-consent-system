package com.consentradar.consentradar.admin;

import com.consentradar.consentradar.entity.Company;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminCompanyService adminCompanyService;

    @Test
    void createCompany_returnsCreated_whenRequestIsValid() throws Exception {
        Company company = new Company();
        company.setCompanyId(1L);
        company.setCompanyName("카카오");
        company.setPackageName("com.kakao.talk");
        company.setPrivacyUrl("https://privacy.example.com");
        company.setIsmsCertified(true);
        when(adminCompanyService.createCompany(any())).thenReturn(company);

        mockMvc.perform(post("/admin/companies")
                        .contentType("application/json")
                        .content("""
                                {"companyName":"카카오","packageName":"com.kakao.talk","privacyUrl":"https://privacy.example.com","ismsCertified":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(1))
                .andExpect(jsonPath("$.companyName").value("카카오"));
    }

    @Test
    void createCompany_returnsConflict_whenPackageNameAlreadyExists() throws Exception {
        when(adminCompanyService.createCompany(any()))
                .thenThrow(new CompanyConflictException("이미 등록된 packageName입니다: com.kakao.talk"));

        mockMvc.perform(post("/admin/companies")
                        .contentType("application/json")
                        .content("""
                                {"companyName":"카카오","packageName":"com.kakao.talk","privacyUrl":"https://privacy.example.com","ismsCertified":false}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteCompany_returnsNoContent_whenDeletedSuccessfully() throws Exception {
        doNothing().when(adminCompanyService).deleteCompany(1L);

        mockMvc.perform(delete("/admin/companies/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCompany_returnsNotFound_whenCompanyDoesNotExist() throws Exception {
        doThrow(new IllegalArgumentException("존재하지 않는 companyId: 999"))
                .when(adminCompanyService).deleteCompany(999L);

        mockMvc.perform(delete("/admin/companies/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCompany_returnsConflict_whenRelatedDataExists() throws Exception {
        doThrow(new CompanyConflictException("연관 데이터가 있어 삭제를 거부합니다"))
                .when(adminCompanyService).deleteCompany(anyLong());

        mockMvc.perform(delete("/admin/companies/1"))
                .andExpect(status().isConflict());
    }
}
