package com.qcomm.engine.dto;

public record CompareResponseDTO(
        RoutingResponseDTO prim,
        RoutingResponseDTO kruskal,
        String recommendedAlgorithm,
        String reason,
        AlgorithmComparisonDetailsDTO details
) {
}
