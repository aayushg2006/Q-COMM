package com.qcomm.engine.dto;

import java.util.List;

public record HistorySummaryDTO(
        Long totalRuns,
        Long latestExecutionTimeMs,
        Integer latestTotalCost,
        List<AlgorithmAnalyticsDTO> algorithmBreakdown
) {
}

