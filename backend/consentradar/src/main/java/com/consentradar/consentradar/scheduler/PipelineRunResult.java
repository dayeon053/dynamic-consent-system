package com.consentradar.consentradar.scheduler;

import java.time.LocalDateTime;

public record PipelineRunResult(
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int totalCompanies,
        int successCount,
        int failCount
) {
}