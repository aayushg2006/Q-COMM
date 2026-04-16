package com.qcomm.engine.dto;

import java.util.List;

public record AlgorithmComparisonDetailsDTO(
        boolean sameCost,
        boolean sameEdgeSet,
        int costDelta,
        long executionTimeDeltaMs,
        int primEdgeCount,
        int kruskalEdgeCount,
        int sharedEdgeCount,
        double edgeOverlapPercent,
        int primDispatchSteps,
        int kruskalDispatchSteps,
        List<RouteEdgeDTO> primOnlyEdges,
        List<RouteEdgeDTO> kruskalOnlyEdges
) {
    public AlgorithmComparisonDetailsDTO {
        primOnlyEdges = List.copyOf(primOnlyEdges);
        kruskalOnlyEdges = List.copyOf(kruskalOnlyEdges);
    }
}
