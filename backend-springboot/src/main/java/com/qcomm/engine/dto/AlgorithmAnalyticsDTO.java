package com.qcomm.engine.dto;

public record AlgorithmAnalyticsDTO(
        String algorithm,
        Long runCount,
        Double avgExecutionTimeMs,
        Double avgTotalCost
) {
}

