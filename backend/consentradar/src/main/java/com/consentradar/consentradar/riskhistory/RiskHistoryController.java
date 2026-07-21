package com.consentradar.consentradar.riskhistory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RiskHistoryController {

    private final PersonalRiskHistoryService personalRiskHistoryService;

    public RiskHistoryController(PersonalRiskHistoryService personalRiskHistoryService) {
        this.personalRiskHistoryService = personalRiskHistoryService;
    }

    @GetMapping("/users/{userId}/companies/{companyId}/risk-history")
    public List<RiskScoreHistoryItemDto> getRiskHistory(@PathVariable Long userId,
                                                          @PathVariable Long companyId) {
        return personalRiskHistoryService.getHistory(userId, companyId);
    }
}
