package com.consentradar.consentradar.crawler;

public record CrawlBatchResult(int totalCompanies, int successCount, int failCount) {
}