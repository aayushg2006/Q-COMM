package com.qcomm.engine.dto;

public record RouteEdgeDTO(
        Long source,
        Long target,
        Integer weight,
        Boolean isRiskFlagged,
        String riskReason
) {
}
