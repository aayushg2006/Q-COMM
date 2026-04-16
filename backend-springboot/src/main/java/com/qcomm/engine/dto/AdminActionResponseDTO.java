package com.qcomm.engine.dto;

public record AdminActionResponseDTO(
        String message,
        Long activeStoreCount,
        Long totalEdgeCount
) {
}

