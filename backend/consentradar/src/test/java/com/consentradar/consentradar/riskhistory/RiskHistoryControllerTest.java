package com.consentradar.consentradar.riskhistory;

import com.consentradar.consentradar.entity.RiskScore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RiskHistoryController.class)
class RiskHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonalRiskHistoryService personalRiskHistoryService;

    @Test
    void getRiskHistory_returnsHistoryAsJsonArray() throws Exception {
        when(personalRiskHistoryService.getHistory(1L, 2L)).thenReturn(List.of(
                new RiskScoreHistoryItemDto(LocalDate.of(2026, 7, 1), BigDecimal.valueOf(10.0), RiskScore.Grade.LOW),
                new RiskScoreHistoryItemDto(LocalDate.of(2026, 7, 2), BigDecimal.valueOf(20.0), RiskScore.Grade.MEDIUM)
        ));

        mockMvc.perform(get("/users/1/companies/2/risk-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].scoredAt").value("2026-07-01"))
                .andExpect(jsonPath("$[0].grade").value("LOW"))
                .andExpect(jsonPath("$[1].scoredAt").value("2026-07-02"))
                .andExpect(jsonPath("$[1].grade").value("MEDIUM"));
    }

    @Test
    void getRiskHistory_returnsEmptyArray_whenNoHistoryExists() throws Exception {
        when(personalRiskHistoryService.getHistory(1L, 2L)).thenReturn(List.of());

        mockMvc.perform(get("/users/1/companies/2/risk-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
