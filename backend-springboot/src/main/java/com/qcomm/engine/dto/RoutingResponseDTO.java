package com.qcomm.engine.dto;

import java.util.List;

public record RoutingResponseDTO(
        List<RouteEdgeDTO> mstEdges,
        Integer totalCost,
        Long executionTimeMs,
        String algorithmUsed,
        WarehouseDTO warehouse,
        List<DispatchStepDTO> dispatchPlan,
        Integer dispatchCost
) {
    public RoutingResponseDTO {
        mstEdges = List.copyOf(mstEdges);
        dispatchPlan = List.copyOf(dispatchPlan);
    }
}
