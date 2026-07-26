package com.consentradar.consentradar.admin;

import com.consentradar.consentradar.scheduler.CompanyCrawlResult;

public record AdminCrawlTriggerResponse(
        Long companyId,
        String companyName,
        boolean changed,
        boolean riskAnalysisTriggered,
        boolean success,
        String message
) {
    public static AdminCrawlTriggerResponse success(CompanyCrawlResult result) {
        return new AdminCrawlTriggerResponse(
                result.companyId(), result.companyName(), result.changed(), result.riskAnalysisTriggered(),
                true, "triggered");
    }

    public static AdminCrawlTriggerResponse failure(Long companyId, String message) {
        return new AdminCrawlTriggerResponse(companyId, null, false, false, false, message);
    }
}
