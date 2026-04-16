package com.qcomm.engine.dto;

import java.time.LocalDateTime;

public record RoutingHistoryDTO(
        Long id,
        LocalDateTime timestamp,
        String algorithmUsed,
        Long executionTimeMs,
        Integer totalCost
) {
}

