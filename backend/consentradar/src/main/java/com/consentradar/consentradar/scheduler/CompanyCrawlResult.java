package com.consentradar.consentradar.scheduler;

public record CompanyCrawlResult(
        Long companyId,
        String companyName,
        boolean changed,
        boolean riskAnalysisTriggered
) {
}
