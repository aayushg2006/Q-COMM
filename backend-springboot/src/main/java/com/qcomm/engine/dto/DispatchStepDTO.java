package com.qcomm.engine.dto;

public record DispatchStepDTO(
        Integer sequence,
        String fromNodeName,
        Double fromLat,
        Double fromLng,
        Long toStoreId,
        String toStoreName,
        Double toLat,
        Double toLng,
        Integer legCost,
        Integer cumulativeCost,
        Boolean isRiskFlagged,
        String riskReason,
        Boolean fromWarehouse
) {
}
