package com.qcomm.engine.daa;

import java.util.List;

public record OptimizationResultDTO(
        List<Graph.Edge> mstEdges,
        int totalCost,
        long executionTimeNs
) {
    public OptimizationResultDTO {
        mstEdges = List.copyOf(mstEdges);
    }
}

