package com.consentradar.consentradar.admin;

import com.consentradar.consentradar.scheduler.CompanyCrawlResult;
import com.consentradar.consentradar.scheduler.PolicyCrawlScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminCrawlController.class)
class AdminCrawlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyCrawlScheduler policyCrawlScheduler;

    @Test
    void triggerCrawl_returnsOk_whenCompanyProcessedSuccessfully() throws Exception {
        when(policyCrawlScheduler.runForCompany(1L))
                .thenReturn(new CompanyCrawlResult(1L, "카카오", true, true));

        mockMvc.perform(post("/admin/crawl/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(1))
                .andExpect(jsonPath("$.companyName").value("카카오"))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.riskAnalysisTriggered").value(true))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void triggerCrawl_returnsNotFound_whenCompanyDoesNotExist() throws Exception {
        when(policyCrawlScheduler.runForCompany(999L))
                .thenThrow(new IllegalArgumentException("존재하지 않는 companyId: 999"));

        mockMvc.perform(post("/admin/crawl/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void triggerCrawl_returnsInternalServerError_whenCrawlFails() throws Exception {
        when(policyCrawlScheduler.runForCompany(1L))
                .thenThrow(new RuntimeException("크롤링 3회 모두 실패"));

        mockMvc.perform(post("/admin/crawl/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("크롤링 3회 모두 실패"));
    }
}
